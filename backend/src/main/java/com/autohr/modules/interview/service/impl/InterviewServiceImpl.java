package com.autohr.modules.interview.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.hr.entity.Department;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.enums.EmploymentStatus;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.AntiCheatEventRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.InterviewProcessTemplateSaveRequest;
import com.autohr.modules.interview.dto.InterviewProcessTemplateStageRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.JobKnowledgeWeightSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeBaseSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeItemSaveRequest;
import com.autohr.modules.interview.dto.LlmConfigSaveRequest;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;
import com.autohr.modules.interview.dto.VideoSignalRequest;
import com.autohr.modules.interview.dto.VideoSignalVO;
import com.autohr.modules.interview.entity.InterviewAiRecord;
import com.autohr.modules.interview.entity.InterviewJobKnowledgeWeight;
import com.autohr.modules.interview.entity.InterviewKnowledgeBase;
import com.autohr.modules.interview.entity.InterviewKnowledgeItem;
import com.autohr.modules.interview.entity.InterviewLlmConfig;
import com.autohr.modules.interview.entity.InterviewProcess;
import com.autohr.modules.interview.entity.InterviewProcessStage;
import com.autohr.modules.interview.entity.InterviewProcessTemplate;
import com.autohr.modules.interview.entity.InterviewProcessTemplateStage;
import com.autohr.modules.interview.entity.InterviewVideoSession;
import com.autohr.modules.interview.mapper.InterviewAiRecordMapper;
import com.autohr.modules.interview.mapper.InterviewJobKnowledgeWeightMapper;
import com.autohr.modules.interview.mapper.InterviewKnowledgeBaseMapper;
import com.autohr.modules.interview.mapper.InterviewKnowledgeItemMapper;
import com.autohr.modules.interview.mapper.InterviewLlmConfigMapper;
import com.autohr.modules.interview.mapper.InterviewProcessMapper;
import com.autohr.modules.interview.mapper.InterviewProcessStageMapper;
import com.autohr.modules.interview.mapper.InterviewProcessTemplateMapper;
import com.autohr.modules.interview.mapper.InterviewProcessTemplateStageMapper;
import com.autohr.modules.interview.mapper.InterviewVideoSessionMapper;
import com.autohr.modules.interview.service.InterviewService;
import com.autohr.modules.interview.service.VideoMergeService;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizer;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerListener;
import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.autohr.modules.system.service.SystemConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    private static final Pattern SDP_ICE_UFRAG_PATTERN = Pattern.compile("(?m)^a=ice-ufrag:([^\\r\\n]+)");
    private static final Pattern CANDIDATE_JSON_UFRAG_PATTERN = Pattern.compile("\\\"usernameFragment\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern CANDIDATE_SDP_UFRAG_PATTERN = Pattern.compile("(?:^|\\s)ufrag\\s+([^\\s]+)");
    private static final String[] STT_CONFIG_KEYS = {
            "ALIYUN_STT_ACCESS_KEY_ID", "ALIYUN_STT_ACCESS_KEY_SECRET", "ALIYUN_STT_APP_KEY", "ALIYUN_STT_ENDPOINT"
    };

    private final InterviewKnowledgeBaseMapper knowledgeBaseMapper;
    private final InterviewKnowledgeItemMapper knowledgeItemMapper;
    private final InterviewJobKnowledgeWeightMapper jobKnowledgeWeightMapper;
    private final InterviewLlmConfigMapper llmConfigMapper;
    private final InterviewProcessMapper processMapper;
    private final InterviewProcessStageMapper processStageMapper;
    private final InterviewProcessTemplateMapper processTemplateMapper;
    private final InterviewProcessTemplateStageMapper processTemplateStageMapper;
    private final InterviewAiRecordMapper aiRecordMapper;
    private final InterviewVideoSessionMapper videoSessionMapper;
    private final RecruitmentCandidateMapper recruitmentCandidateMapper;
    private final RecruitmentJobMapper recruitmentJobMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final VideoMergeService videoMergeService;
    private final S3ObjectStorageService s3ObjectStorageService;
    private final SystemConfigService systemConfigService;
    private final TransactionTemplate transactionTemplate;

    @Value("${interview.llm.debug:false}")
    private boolean llmDebug;

    private static final long MAX_RECORDING_SIZE = 100 * 1024 * 1024;
    private static final int MAX_ICE_CANDIDATE_LENGTH = 4096;
    private static final int MAX_ICE_CANDIDATE_COUNT = 256;
    private static final int MAX_ICE_CANDIDATES_LENGTH = 256 * 1024;
    private static final int ICE_APPEND_MAX_ATTEMPTS = 5;
    private static final int VIDEO_MERGE_MAX_ATTEMPTS = 3;
    private static final long MISSING_RECORDING_TIMEOUT_MINUTES = 10L;
    private static final Set<String> ALLOWED_RECORDING_CONTENT_TYPES = Set.of("video/webm", "application/octet-stream");
    private static final Set<String> ALLOWED_ANTI_CHEAT_EVENTS = Set.of(
            "AI_RECORDING_DENIED", "AI_RECORDING_STARTED", "AI_RECORDING_UNSUPPORTED", "AI_RECORDING_UPLOADED",
            "CLIPBOARD_BLOCKED", "FULLSCREEN_DENIED", "FULLSCREEN_EXIT", "TAB_HIDDEN", "WINDOW_BLUR"
    );

    @Scheduled(fixedDelayString = "${interview.video.missing-recording-scan-interval-ms:60000}")
    public void releaseTimedOutVideoUploadsForApproval() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(MISSING_RECORDING_TIMEOUT_MINUTES);
        List<Long> sessionIds = videoSessionMapper.selectList(new LambdaQueryWrapper<InterviewVideoSession>()
                        .eq(InterviewVideoSession::getSessionStatus, "END_REQUESTED")
                        .isNotNull(InterviewVideoSession::getEndTime)
                        .le(InterviewVideoSession::getEndTime, cutoff))
                .stream().map(InterviewVideoSession::getId).toList();
        for (Long sessionId : sessionIds) {
            try {
                transactionTemplate.execute(status -> markMissingRecordingForApproval(sessionId, cutoff, status));
            } catch (RuntimeException ex) {
                log.warn("Unable to release video session {} after recording upload timeout", sessionId, ex);
            }
        }
    }

    @Override
    @Transactional
    public InterviewVO saveKnowledgeBase(KnowledgeBaseSaveRequest request) {
        InterviewKnowledgeBase entity = request.getId() == null ? new InterviewKnowledgeBase() : requireKnowledgeBase(request.getId());
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            knowledgeBaseMapper.insert(entity);
        } else {
            knowledgeBaseMapper.updateById(entity);
        }
        return toKnowledgeBaseVO(entity);
    }

    @Override
    public List<InterviewVO> listKnowledgeBases(Integer status, String keyword) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<InterviewKnowledgeBase>()
                .eq(status != null, InterviewKnowledgeBase::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewKnowledgeBase::getKnowledgeBaseName, keyword)
                        .or().like(InterviewKnowledgeBase::getTechCategory, keyword)
                        .or().like(InterviewKnowledgeBase::getJobCategory, keyword))
                .orderByDesc(InterviewKnowledgeBase::getId)).stream().map(this::toKnowledgeBaseVO).toList();
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        knowledgeItemMapper.delete(new LambdaQueryWrapper<InterviewKnowledgeItem>().eq(InterviewKnowledgeItem::getKnowledgeBaseId, id));
        jobKnowledgeWeightMapper.delete(new LambdaQueryWrapper<InterviewJobKnowledgeWeight>().eq(InterviewJobKnowledgeWeight::getKnowledgeBaseId, id));
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public InterviewVO saveKnowledgeItem(KnowledgeItemSaveRequest request) {
        requireKnowledgeBase(request.getKnowledgeBaseId());
        InterviewKnowledgeItem entity = request.getId() == null ? new InterviewKnowledgeItem() : requireKnowledgeItem(request.getId());
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            knowledgeItemMapper.insert(entity);
        } else {
            knowledgeItemMapper.updateById(entity);
        }
        return toKnowledgeItemVO(entity);
    }

    @Override
    @Transactional
    public int importKnowledgeItems(Long knowledgeBaseId, MultipartFile file) {
        requireKnowledgeBase(knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CSV文件不能为空");
        }
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "knowledge-items.csv").toLowerCase();
        if (!originalName.endsWith(".csv")) {
            throw new BusinessException("仅支持CSV文件");
        }
        List<List<String>> rows = parseCsv(file);
        if (rows.isEmpty()) {
            throw new BusinessException("CSV文件没有可导入内容");
        }
        int startIndex = isKnowledgeItemHeader(rows.get(0)) ? 1 : 0;
        int imported = 0;
        for (int i = startIndex; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String point = csvValue(row, 0);
            String content = csvValue(row, 1);
            if (StrUtil.isBlank(point) && StrUtil.isBlank(content)) {
                continue;
            }
            if (StrUtil.isBlank(point) || StrUtil.isBlank(content)) {
                throw new BusinessException("CSV第" + (i + 1) + "行知识点或知识内容为空");
            }
            InterviewKnowledgeItem entity = new InterviewKnowledgeItem();
            entity.setKnowledgeBaseId(knowledgeBaseId);
            entity.setKnowledgePoint(point);
            entity.setKnowledgeContent(content);
            entity.setStatus(parseCsvStatus(csvValue(row, 2)));
            knowledgeItemMapper.insert(entity);
            imported++;
        }
        if (imported == 0) {
            throw new BusinessException("CSV文件没有可导入内容");
        }
        return imported;
    }

    @Override
    public List<InterviewVO> listKnowledgeItems(Long knowledgeBaseId, String keyword) {
        return knowledgeItemMapper.selectList(new LambdaQueryWrapper<InterviewKnowledgeItem>()
                .eq(knowledgeBaseId != null, InterviewKnowledgeItem::getKnowledgeBaseId, knowledgeBaseId)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewKnowledgeItem::getKnowledgePoint, keyword)
                        .or().like(InterviewKnowledgeItem::getKnowledgeContent, keyword))
                .orderByAsc(InterviewKnowledgeItem::getId)).stream().map(this::toKnowledgeItemVO).toList();
    }

    private List<List<String>> parseCsv(MultipartFile file) {
        String text;
        try {
            byte[] bytes = file.getBytes();
            text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            if (text.contains("�")) {
                text = new String(bytes, Charset.forName("GBK"));
            }
        } catch (IOException ex) {
            throw new BusinessException("CSV文件读取失败: " + ex.getMessage());
        }
        List<List<String>> rows = new java.util.ArrayList<>();
        List<String> row = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString().trim());
                rows.add(row);
                row = new java.util.ArrayList<>();
                cell.setLength(0);
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        row.add(cell.toString().trim());
        if (row.stream().anyMatch(StrUtil::isNotBlank)) {
            rows.add(row);
        }
        return rows.stream().filter(item -> item.stream().anyMatch(StrUtil::isNotBlank)).toList();
    }

    private boolean isKnowledgeItemHeader(List<String> row) {
        return StrUtil.equalsAnyIgnoreCase(csvValue(row, 0), "knowledgePoint", "知识点")
                && StrUtil.equalsAnyIgnoreCase(csvValue(row, 1), "knowledgeContent", "知识内容");
    }

    private String csvValue(List<String> row, int index) {
        return index < row.size() ? StrUtil.trim(row.get(index)) : "";
    }

    private Integer parseCsvStatus(String value) {
        if (StrUtil.isBlank(value)) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException("CSV状态必须为数字，建议填写1启用或0停用");
        }
    }

    @Override
    @Transactional
    public void deleteKnowledgeItem(Long id) {
        knowledgeItemMapper.deleteById(id);
    }

    @Override
    @Transactional
    public InterviewVO saveJobKnowledgeWeight(JobKnowledgeWeightSaveRequest request) {
        requireRecruitmentJob(request.getJobId());
        requireKnowledgeBase(request.getKnowledgeBaseId());
        InterviewJobKnowledgeWeight entity = request.getId() == null ? new InterviewJobKnowledgeWeight() : requireJobKnowledgeWeight(request.getId());
        BeanUtils.copyProperties(request, entity);
        if (request.getId() == null) {
            jobKnowledgeWeightMapper.insert(entity);
        } else {
            jobKnowledgeWeightMapper.updateById(entity);
        }
        return toJobKnowledgeWeightVO(entity);
    }

    @Override
    public List<InterviewVO> listJobKnowledgeWeights(Long jobId) {
        return jobKnowledgeWeightMapper.selectList(new LambdaQueryWrapper<InterviewJobKnowledgeWeight>()
                .eq(jobId != null, InterviewJobKnowledgeWeight::getJobId, jobId)
                .orderByDesc(InterviewJobKnowledgeWeight::getWeight)
                .orderByAsc(InterviewJobKnowledgeWeight::getId)).stream().map(this::toJobKnowledgeWeightVO).toList();
    }

    @Override
    @Transactional
    public void deleteJobKnowledgeWeight(Long id) {
        jobKnowledgeWeightMapper.deleteById(id);
    }

    @Override
    @Transactional
    public InterviewVO saveLlmConfig(LlmConfigSaveRequest request) {
        InterviewLlmConfig entity = request.getId() == null ? new InterviewLlmConfig() : requireLlmConfig(request.getId());
        String existingApiKey = entity.getApiKey();
        BeanUtils.copyProperties(request, entity);
        if (request.getId() != null && StrUtil.isBlank(request.getApiKey())) {
            entity.setApiKey(existingApiKey);
        }
        entity.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            llmConfigMapper.insert(entity);
        } else {
            llmConfigMapper.updateById(entity);
        }
        return toLlmConfigVO(entity);
    }

    @Override
    public List<InterviewVO> listLlmConfigs(String modelRole, Integer status) {
        return llmConfigMapper.selectList(new LambdaQueryWrapper<InterviewLlmConfig>()
                .eq(StrUtil.isNotBlank(modelRole), InterviewLlmConfig::getModelRole, modelRole)
                .eq(status != null, InterviewLlmConfig::getStatus, status)
                .orderByDesc(InterviewLlmConfig::getId)).stream().map(this::toLlmConfigVO).toList();
    }

    @Override
    @Transactional
    public void deleteLlmConfig(Long id) {
        llmConfigMapper.deleteById(id);
    }

    @Override
    @Transactional
    public InterviewVO saveProcessTemplate(InterviewProcessTemplateSaveRequest request) {
        validateTemplateStages(request.getStages());
        InterviewProcessTemplate template = request.getId() == null
                ? new InterviewProcessTemplate()
                : requireProcessTemplate(request.getId());
        template.setTemplateName(abbreviate(request.getTemplateName().trim(), 128));
        template.setDescription(abbreviate(StrUtil.blankToDefault(request.getDescription(), ""), 1000));
        template.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (template.getId() == null) {
            processTemplateMapper.insert(template);
        } else {
            processTemplateMapper.updateById(template);
            processTemplateStageMapper.delete(new LambdaQueryWrapper<InterviewProcessTemplateStage>()
                    .eq(InterviewProcessTemplateStage::getTemplateId, template.getId()));
        }
        for (int index = 0; index < request.getStages().size(); index++) {
            InterviewProcessTemplateStageRequest item = request.getStages().get(index);
            InterviewProcessTemplateStage stage = new InterviewProcessTemplateStage();
            stage.setTemplateId(template.getId());
            stage.setStageName(abbreviate(item.getStageName().trim(), 128));
            stage.setStageType(normalizeTemplateStageType(item.getStageType()));
            stage.setKnowledgeBaseId(stage.getStageType().equals("AI") ? item.getKnowledgeBaseId() : null);
            stage.setSequenceNo(index + 1);
            processTemplateStageMapper.insert(stage);
        }
        return toProcessTemplateVO(template);
    }

    @Override
    public List<InterviewVO> listProcessTemplates(Integer status, String keyword) {
        return processTemplateMapper.selectList(new LambdaQueryWrapper<InterviewProcessTemplate>()
                .eq(status != null, InterviewProcessTemplate::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), query -> query.like(InterviewProcessTemplate::getTemplateName, keyword)
                        .or().like(InterviewProcessTemplate::getDescription, keyword))
                .orderByDesc(InterviewProcessTemplate::getId)).stream().map(this::toProcessTemplateVO).toList();
    }

    @Override
    public InterviewVO getProcessTemplate(Long id) {
        return toProcessTemplateVO(requireProcessTemplate(id));
    }

    @Override
    @Transactional
    public void deleteProcessTemplate(Long id) {
        if (processMapper.selectCount(new LambdaQueryWrapper<InterviewProcess>().eq(InterviewProcess::getTemplateId, id)) > 0) {
            throw new BusinessException("该模板已用于面试流程，不能删除；可改为停用");
        }
        processTemplateStageMapper.delete(new LambdaQueryWrapper<InterviewProcessTemplateStage>()
                .eq(InterviewProcessTemplateStage::getTemplateId, id));
        processTemplateMapper.deleteById(id);
    }

    @Override
    @Transactional
    public InterviewVO startInterviewProcess(StartInterviewProcessRequest request) {
        RecruitmentCandidate candidate = requireRecruitmentCandidate(request.getRecruitmentCandidateId());
        requireRecruitmentJob(request.getJobId());
        if (!Objects.equals(candidate.getJobId(), request.getJobId())) {
            throw new BusinessException("候选人不属于所选岗位");
        }
        if (!Objects.equals(candidate.getIntervieweeUserId(), request.getIntervieweeUserId())) {
            throw new BusinessException("候选人与面试者账号不匹配");
        }
        if (candidate.getId() == null) {
            throw new BusinessException("候选人唯一ID不存在，不能发起面试流程");
        }
        InterviewProcess existingProcess = processMapper.selectOne(new LambdaQueryWrapper<InterviewProcess>()
                .eq(InterviewProcess::getRecruitmentCandidateId, candidate.getId())
                .last("LIMIT 1"));
        if (candidate.getInterviewProcessId() != null || existingProcess != null) {
            throw new BusinessException("该候选人已存在面试流程");
        }
        InterviewProcess process = new InterviewProcess();
        process.setRecruitmentCandidateId(candidate.getId());
        process.setIntervieweeUserId(request.getIntervieweeUserId());
        process.setJobId(request.getJobId());
        InterviewProcessTemplate template = request.getTemplateId() == null ? null : requireActiveProcessTemplate(request.getTemplateId());
        List<InterviewProcessTemplateStage> templateStages = template == null ? List.of() : listTemplateStages(template.getId());
        InterviewProcessTemplateStage firstTemplateStage = templateStages.isEmpty() ? null : templateStages.get(0);
        process.setTemplateId(template == null ? null : template.getId());
        process.setTemplateName(template == null ? null : template.getTemplateName());
        process.setCurrentStage(firstTemplateStage == null ? "AI" : firstTemplateStage.getStageType());
        process.setStageStatus(firstTemplateStage != null && "VIDEO".equals(firstTemplateStage.getStageType()) ? "READY" : "IN_PROGRESS");
        process.setOverallStatus("IN_PROGRESS");
        process.setAiThresholdScore(normalizeScore(Objects.requireNonNullElse(request.getAiThresholdScore(), 70)));
        process.setAiFollowUpThreshold(Math.max(0, Math.min(Objects.requireNonNullElse(request.getAiFollowUpThreshold(), 70), 100)));
        int minQuestionRounds = Math.max(Objects.requireNonNullElse(request.getAiMinQuestionRounds(), 5), 1);
        int maxQuestionRounds = Math.max(Objects.requireNonNullElse(request.getAiMaxQuestionRounds(), 10), minQuestionRounds);
        process.setAiMinQuestionRounds(minQuestionRounds);
        process.setAiMaxQuestionRounds(maxQuestionRounds);
        process.setAntiCheatSwitchLimit(Math.max(Objects.requireNonNullElse(request.getAntiCheatSwitchLimit(), 5), 1));
        process.setAntiCheatSwitchCount(0);
        process.setAiOutputMode(normalizeAiOutputMode(request.getAiOutputMode()));
        process.setVideoApproved(0);
        process.setOnsiteApproved(0);
        process.setProcessStatusView(firstTemplateStage == null ? "AI面" : stageStatusView(firstTemplateStage.getStageName(), process.getStageStatus()));
        try {
            processMapper.insert(process);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("该候选人已存在面试流程");
        }
        if (template != null) {
            cloneTemplateStages(process, templateStages);
            if ("VIDEO".equals(process.getCurrentStage())) {
                InterviewProcessStage firstStage = requireActiveProcessStage(process);
                ensureVideoSession(process.getId(), firstStage.getId(), null, null);
            }
        }
        candidate.setInterviewProcessId(process.getId());
        candidate.setInterviewStageStatus(process.getProcessStatusView());
        candidate.setApplicationStatus("INTERVIEWING");
        recruitmentCandidateMapper.updateById(candidate);
        if ("AI".equals(process.getCurrentStage())) {
            runAfterCommit(() -> CompletableFuture.runAsync(() -> generateInitialQuestionSafely(process.getId())));
        }
        return toProcessVO(process);
    }

    @Override
    public List<InterviewVO> listProcesses(String overallStatus, String stageStatus, String keyword) {
        return processMapper.selectList(new LambdaQueryWrapper<InterviewProcess>()
                .eq(StrUtil.isNotBlank(overallStatus), InterviewProcess::getOverallStatus, overallStatus)
                .eq(StrUtil.isNotBlank(stageStatus), InterviewProcess::getStageStatus, stageStatus)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewProcess::getProcessStatusView, keyword)
                        .or().like(InterviewProcess::getApprovedHrName, keyword))
                .orderByDesc(InterviewProcess::getId)).stream().map(this::toProcessVO).toList();
    }

    @Override
    public InterviewVO getProcess(Long processId) {
        return toProcessVO(requireProcess(processId));
    }

    @Override
    public InterviewVO getProcessStage(Long processId, Long processStageId) {
        InterviewProcessStage stage = processStageMapper.selectById(processStageId);
        if (stage == null || !Objects.equals(stage.getProcessId(), processId)) {
            throw new BusinessException("面试流程阶段不存在");
        }
        return toProcessStageVO(stage);
    }

    @Override
    public InterviewVO getIntervieweeProcess(Long processId, Long intervieweeUserId) {
        return toIntervieweeProcessVO(requireIntervieweeProcess(processId, intervieweeUserId));
    }

    @Override
    public synchronized InterviewVO getNextAiQuestion(Long processId) {
        InterviewProcess process = requireProcess(processId);
        if (isTemplateProcess(process)) {
            return getTemplateNextAiQuestion(process);
        }
        if (!StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            return null;
        }
        InterviewAiRecord unanswered = aiRecordMapper.selectOne(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessId, processId)
                .isNull(InterviewAiRecord::getAnswerContent)
                .orderByAsc(InterviewAiRecord::getSequenceNo)
                .last("LIMIT 1"));
        if (unanswered == null && StrUtil.equals(process.getCurrentStage(), "AI")) {
            int recordCount = aiRecordMapper.selectCount(new LambdaQueryWrapper<InterviewAiRecord>()
                    .eq(InterviewAiRecord::getProcessId, processId)).intValue();
            if (recordCount == 0) {
                generateInitialQuestionSafely(processId);
                unanswered = aiRecordMapper.selectOne(new LambdaQueryWrapper<InterviewAiRecord>()
                        .eq(InterviewAiRecord::getProcessId, processId)
                        .isNull(InterviewAiRecord::getAnswerContent)
                        .orderByAsc(InterviewAiRecord::getSequenceNo)
                        .last("LIMIT 1"));
            }
        }
        return unanswered == null ? null : toAiRecordVO(unanswered, process);
    }

    @Override
    public InterviewVO getIntervieweeNextAiQuestion(Long processId, Long intervieweeUserId) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        return getNextAiQuestion(processId);
    }

    @Override
    @Transactional
    public synchronized InterviewVO submitAiAnswer(AiAnswerRequest request) {
        return submitAiAnswer(request, null);
    }

    private synchronized InterviewVO submitAiAnswer(AiAnswerRequest request, Consumer<String> interviewerChunkConsumer) {
        InterviewProcess process = requireProcess(request.getProcessId());
        InterviewAiRecord requestedRecord = requireRequestedAiRecord(process, request);
        if (requestedRecord.getAnswerContent() != null) {
            if (StrUtil.equals(requestedRecord.getAnswerContent(), request.getAnswerContent())) {
                return toAiRecordVO(requestedRecord, process);
            }
            throw new BusinessException("该题已提交，不能修改回答");
        }
        if (isTemplateProcess(process)) {
            return submitTemplateAiAnswer(process, request, requestedRecord, interviewerChunkConsumer);
        }
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "AI")) {
            throw new BusinessException("当前流程不在AI面试阶段");
        }
        InterviewAiRecord record = requestedRecord;
        record.setAnswerContent(request.getAnswerContent());
        String materials = loadKnowledgeMaterials(record.getKnowledgeBaseId());
        String jobRequirements = loadJobRequirements(process);
        LlmEvaluation interviewerEvaluation = callLlmEvaluation(record.getQuestionContent(), request.getAnswerContent(), record.getKnowledgePoint(), materials, jobRequirements, "INTERVIEWER", true, interviewerChunkConsumer);
        LlmEvaluation scorerEvaluation = callLlmEvaluation(record.getQuestionContent(), request.getAnswerContent(), record.getKnowledgePoint(), materials, jobRequirements, "SCORER", false);
        int interviewerScore = interviewerEvaluation.score();
        int scorerScore = scorerEvaluation.score();
        int averageScore = Math.round((interviewerScore + scorerScore) / 2.0f);
        record.setInterviewerScore(interviewerScore);
        record.setScorerScore(scorerScore);
        record.setAverageScore(averageScore);
        record.setInterviewerComment(interviewerEvaluation.comment());
        aiRecordMapper.updateById(record);

        int total = aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessId, process.getId())
                .isNotNull(InterviewAiRecord::getAverageScore))
                .stream().mapToInt(InterviewAiRecord::getAverageScore).sum();
        int count = Math.max(aiRecordMapper.selectCount(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessId, process.getId())
                .isNotNull(InterviewAiRecord::getAverageScore)).intValue(), 1);
        int currentAverage = Math.round(total / (float) count);
        process.setAiAverageScore(currentAverage);
        int answeredRounds = count;
        int minQuestionRounds = Math.max(Objects.requireNonNullElse(process.getAiMinQuestionRounds(), 5), 1);
        int maxQuestionRounds = Math.max(Objects.requireNonNullElse(process.getAiMaxQuestionRounds(), 10), 1);
        int followUpThreshold = Math.max(0, Math.min(Objects.requireNonNullElse(process.getAiFollowUpThreshold(), 70), 100));
        boolean needsFollowUp = averageScore < followUpThreshold && answeredRounds < maxQuestionRounds;
        if (answeredRounds >= minQuestionRounds && currentAverage >= process.getAiThresholdScore() && !needsFollowUp) {
            process.setStageStatus("WAITING_APPROVAL");
            process.setProcessStatusView("AI待审批");
        } else if (answeredRounds >= maxQuestionRounds) {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
            process.setProcessStatusView("AI未达标自动结束");
            auditLogService.log(process.getIntervieweeUserId(), "面试者", "INTERVIEWEE", "INTERVIEW", "AI_MAX_ROUNDS_REJECT", "INTERVIEW_PROCESS", String.valueOf(process.getId()), "AI均分" + currentAverage + "未达到阈值" + process.getAiThresholdScore() + "，已答" + answeredRounds + "轮达到最大轮数" + process.getAiMaxQuestionRounds());
        } else {
            if (needsFollowUp) {
                generateFollowUpQuestion(process, record, request.getAnswerContent(), interviewerEvaluation.nextQuestion());
            } else {
                generateNextQuestion(process);
            }
        }
        processMapper.updateById(process);
        updateCandidateStage(process);
        return toAiRecordVO(record, process);
    }

    @Override
    @Transactional
    public InterviewVO submitIntervieweeAiAnswer(AiAnswerRequest request, Long intervieweeUserId) {
        requireIntervieweeProcess(request.getProcessId(), intervieweeUserId);
        return submitAiAnswer(request);
    }

    @Override
    public SseEmitter submitIntervieweeAiAnswerStream(AiAnswerRequest request, Long intervieweeUserId) {
        requireIntervieweeProcess(request.getProcessId(), intervieweeUserId);
        SseEmitter emitter = new SseEmitter(180000L);
        CompletableFuture.runAsync(() -> {
            try {
                InterviewVO result = submitAiAnswerInTransaction(request, chunk -> {
                    try {
                        sendSse(emitter, "token", chunk);
                    } catch (IOException ex) {
                        throw new BusinessException("流式输出失败: " + abbreviate(ex.getMessage()));
                    }
                });
                sendSse(emitter, "done", result);
                emitter.complete();
            } catch (Exception ex) {
                String errorId = UUID.randomUUID().toString();
                log.warn("AI answer stream failed [{}] for process {}", errorId, request.getProcessId(), ex);
                try {
                    sendSse(emitter, "error", "AI处理失败，请稍后重试（错误编号：" + errorId + "）");
                } catch (IOException ignored) {
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void sendSse(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private InterviewVO submitAiAnswerInTransaction(AiAnswerRequest request, Consumer<String> interviewerChunkConsumer) {
        InterviewVO result = transactionTemplate.execute(status -> submitAiAnswer(request, interviewerChunkConsumer));
        if (result == null) {
            throw new BusinessException("AI answer submission did not return a result");
        }
        return result;
    }

    @Override
    public List<InterviewVO> listAiRecords(Long processId) {
        Map<Long, Integer> stageOrder = new HashMap<>();
        if (processId != null) {
            listProcessStages(processId).forEach(stage -> stageOrder.put(
                    stage.getId(), Objects.requireNonNullElse(stage.getSequenceNo(), Integer.MAX_VALUE)));
        }
        return aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(processId != null, InterviewAiRecord::getProcessId, processId)
                .orderByAsc(InterviewAiRecord::getSequenceNo)
                .orderByAsc(InterviewAiRecord::getId)).stream()
                .sorted(Comparator
                        .comparingInt((InterviewAiRecord item) -> stageOrder.getOrDefault(item.getProcessStageId(), Integer.MAX_VALUE))
                        .thenComparingInt(item -> Objects.requireNonNullElse(item.getSequenceNo(), Integer.MAX_VALUE))
                        .thenComparingLong(item -> Objects.requireNonNullElse(item.getId(), Long.MAX_VALUE)))
                .map(item -> toAiRecordVO(item, null))
                .toList();
    }

    @Override
    public List<InterviewVO> listIntervieweeAiRecords(Long processId, Long intervieweeUserId) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        return listAiRecords(processId);
    }

    private InterviewAiRecord requireRequestedAiRecord(InterviewProcess process, AiAnswerRequest request) {
        InterviewAiRecord record = aiRecordMapper.selectById(request.getQuestionId());
        if (record == null || !Objects.equals(record.getProcessId(), process.getId())) {
            throw new BusinessException("请求的题目不属于当前面试流程");
        }
        return record;
    }

    private InterviewVO getTemplateNextAiQuestion(InterviewProcess process) {
        if (!StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            return null;
        }
        InterviewProcessStage stage = requireActiveProcessStage(process);
        if (!"AI".equals(stage.getStageType()) || !"IN_PROGRESS".equals(stage.getStageStatus())) {
            return null;
        }
        InterviewAiRecord unanswered = aiRecordMapper.selectOne(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessStageId, stage.getId())
                .isNull(InterviewAiRecord::getAnswerContent)
                .orderByAsc(InterviewAiRecord::getSequenceNo)
                .last("LIMIT 1"));
        if (unanswered == null && aiRecordMapper.selectCount(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessStageId, stage.getId())) == 0) {
            generateInitialQuestionSafely(process.getId());
            unanswered = aiRecordMapper.selectOne(new LambdaQueryWrapper<InterviewAiRecord>()
                    .eq(InterviewAiRecord::getProcessStageId, stage.getId())
                    .isNull(InterviewAiRecord::getAnswerContent)
                    .orderByAsc(InterviewAiRecord::getSequenceNo)
                    .last("LIMIT 1"));
        }
        return unanswered == null ? null : toAiRecordVO(unanswered, process);
    }

    private InterviewVO submitTemplateAiAnswer(InterviewProcess process, AiAnswerRequest request, InterviewAiRecord record, Consumer<String> interviewerChunkConsumer) {
        ensureInProgress(process);
        InterviewProcessStage stage = requireActiveProcessStage(process);
        if (!"AI".equals(stage.getStageType()) || !"IN_PROGRESS".equals(stage.getStageStatus())) {
            throw new BusinessException("当前流程不在AI面试阶段");
        }
        if (!Objects.equals(record.getProcessStageId(), stage.getId())) {
            throw new BusinessException("该题不属于当前 AI 面试阶段");
        }
        record.setAnswerContent(request.getAnswerContent());
        String materials = loadKnowledgeMaterials(record.getKnowledgeBaseId());
        String jobRequirements = loadJobRequirements(process);
        LlmEvaluation interviewerEvaluation = callLlmEvaluation(record.getQuestionContent(), request.getAnswerContent(), record.getKnowledgePoint(), materials, jobRequirements, "INTERVIEWER", true, interviewerChunkConsumer);
        LlmEvaluation scorerEvaluation = callLlmEvaluation(record.getQuestionContent(), request.getAnswerContent(), record.getKnowledgePoint(), materials, jobRequirements, "SCORER", false);
        int averageScore = Math.round((interviewerEvaluation.score() + scorerEvaluation.score()) / 2.0f);
        record.setInterviewerScore(interviewerEvaluation.score());
        record.setScorerScore(scorerEvaluation.score());
        record.setAverageScore(averageScore);
        record.setInterviewerComment(interviewerEvaluation.comment());
        aiRecordMapper.updateById(record);

        List<InterviewAiRecord> answered = aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessStageId, stage.getId())
                .isNotNull(InterviewAiRecord::getAverageScore));
        int answeredRounds = answered.size();
        int currentAverage = Math.round(answered.stream().mapToInt(InterviewAiRecord::getAverageScore).sum() / (float) Math.max(answeredRounds, 1));
        process.setAiAverageScore(currentAverage);
        int minQuestionRounds = Math.max(Objects.requireNonNullElse(process.getAiMinQuestionRounds(), 5), 1);
        int maxQuestionRounds = Math.max(Objects.requireNonNullElse(process.getAiMaxQuestionRounds(), 10), minQuestionRounds);
        int followUpThreshold = Math.max(0, Math.min(Objects.requireNonNullElse(process.getAiFollowUpThreshold(), 70), 100));
        boolean needsFollowUp = averageScore < followUpThreshold && answeredRounds < maxQuestionRounds;
        if (answeredRounds >= minQuestionRounds && currentAverage >= process.getAiThresholdScore() && !needsFollowUp) {
            setTemplateStageStatus(process, stage, "WAITING_APPROVAL");
        } else if (answeredRounds >= maxQuestionRounds) {
            rejectTemplateProcess(process, stage, "未达到AI通过阈值");
        } else if (needsFollowUp) {
            generateFollowUpQuestion(process, record, request.getAnswerContent(), interviewerEvaluation.nextQuestion());
        } else {
            generateNextQuestion(process);
        }
        processMapper.updateById(process);
        updateCandidateStage(process);
        return toAiRecordVO(record, process);
    }

    @Override
    @Transactional
    public InterviewVO createVideoSession(Long processId, Long approverUserId, String approverName) {
        InterviewProcess process = requireProcess(processId);
        if (isTemplateProcess(process)) {
            return createTemplateVideoSession(process, approverUserId, approverName);
        }
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "VIDEO")) {
            throw new BusinessException("当前流程不在视频面试阶段");
        }
        InterviewVideoSession session = ensureVideoSession(processId, approverUserId, approverName);
        auditLogService.log(approverUserId, displayName(approverName, "HR"), "HR_ADMIN", "INTERVIEW", "CREATE_VIDEO_SESSION", "VIDEO_SESSION", String.valueOf(session.getId()), session.getVideoSerialNo());
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO intervieweeJoinVideo(Long processId, Long intervieweeUserId, String intervieweeName) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (session.getIntervieweeJoinTime() != null) {
            return toIntervieweeVideoSessionVO(session);
        }
        session.setIntervieweeJoinTime(LocalDateTime.now());
        if (session.getStartTime() == null) {
            session.setStartTime(LocalDateTime.now());
        }
        session.setSessionStatus("INTERVIEWEE_JOINED");
        videoSessionMapper.updateById(session);
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "INTERVIEWEE_JOIN_VIDEO", "VIDEO_SESSION", String.valueOf(session.getId()), String.valueOf(processId));
        return toIntervieweeVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO hrJoinVideo(Long processId, Long approverUserId, String approverName) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (session.getHrJoinTime() != null) {
            return toVideoSessionVO(session);
        }
        session.setApproverUserId(approverUserId);
        session.setApproverName(approverName);
        session.setHrJoinTime(LocalDateTime.now());
        session.setStartTime(session.getStartTime() == null ? LocalDateTime.now() : session.getStartTime());
        session.setSessionStatus(canStartSynchronizedRecording(session) ? "RECORDING" : "HR_JOINED");
        videoSessionMapper.updateById(session);
        auditLogService.log(approverUserId, displayName(approverName, "HR"), "HR_ADMIN", "INTERVIEW", "HR_JOIN_VIDEO", "VIDEO_SESSION", String.valueOf(session.getId()), String.valueOf(processId));
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO completeVideoSession(Long processId, String recordingPath) {
        InterviewProcess templateProcess = requireProcess(processId);
        if (isTemplateProcess(templateProcess)) {
            return completeTemplateVideoSession(templateProcess, recordingPath);
        }
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (isTerminalVideoSessionStatus(session.getSessionStatus())) {
            return toVideoSessionVO(session);
        }
        if (!isTerminalVideoSessionStatus(session.getSessionStatus()) && !StrUtil.equals(session.getSessionStatus(), "END_REQUESTED")) {
            session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
            session.setRecordingEndRequestedAt(session.getRecordingEndRequestedAt() == null ? LocalDateTime.now().plusSeconds(3) : session.getRecordingEndRequestedAt());
            session.setSessionStatus("END_REQUESTED");
            videoSessionMapper.updateById(session);
        }
        InterviewProcess process = requireProcess(processId);
        if (StrUtil.equals(process.getCurrentStage(), "VIDEO") && StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            if (videoMergeService.canMerge(session)) {
                prepareVideoMerge(session);
                videoSessionMapper.updateById(session);
                scheduleVideoMergeAndSummary(session.getId());
            } else {
                process.setStageStatus("UPLOADING");
                process.setProcessStatusView("视频录制上传中");
                processMapper.updateById(process);
                updateCandidateStage(process);
            }
        }
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO requestIntervieweeVideoEnd(Long processId, Long intervieweeUserId) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        completeVideoSession(processId, null);
        return toIntervieweeVideoSessionVO(requireVideoSessionByProcess(processId));
    }

    private InterviewVO createTemplateVideoSession(InterviewProcess process, Long approverUserId, String approverName) {
        ensureInProgress(process);
        InterviewProcessStage stage = requireActiveProcessStage(process);
        if (!"VIDEO".equals(stage.getStageType())) {
            throw new BusinessException("当前流程不在视频面试阶段");
        }
        InterviewVideoSession session = ensureVideoSession(process.getId(), stage.getId(), approverUserId, approverName);
        auditLogService.log(approverUserId, displayName(approverName, "HR"), "HR_ADMIN", "INTERVIEW", "CREATE_VIDEO_SESSION", "VIDEO_SESSION", String.valueOf(session.getId()), session.getVideoSerialNo());
        return toVideoSessionVO(session);
    }

    private InterviewVO completeTemplateVideoSession(InterviewProcess process, String recordingPath) {
        InterviewProcessStage stage = requireActiveProcessStage(process);
        if (!"VIDEO".equals(stage.getStageType())) {
            throw new BusinessException("当前流程不在视频面试阶段");
        }
        InterviewVideoSession session = requireVideoSessionByProcessStage(process.getId(), stage.getId());
        if (isTerminalVideoSessionStatus(session.getSessionStatus())) {
            return toVideoSessionVO(session);
        }
        if (!StrUtil.equals(session.getSessionStatus(), "END_REQUESTED")) {
            session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
            session.setRecordingEndRequestedAt(session.getRecordingEndRequestedAt() == null ? LocalDateTime.now().plusSeconds(3) : session.getRecordingEndRequestedAt());
            session.setSessionStatus("END_REQUESTED");
            videoSessionMapper.updateById(session);
        }
        if (videoMergeService.canMerge(session)) {
            prepareVideoMerge(session);
            videoSessionMapper.updateById(session);
            scheduleVideoMergeAndSummary(session.getId());
        } else {
            setTemplateStageStatus(process, stage, "UPLOADING");
            processMapper.updateById(process);
            updateCandidateStage(process);
        }
        return toVideoSessionVO(session);
    }

    private InterviewVO decideTemplateStage(InterviewProcess process, String expectedType, InterviewDecisionRequest request) {
        ensureInProgress(process);
        InterviewProcessStage stage = requireActiveProcessStage(process);
        if (!expectedType.equals(stage.getStageType())) {
            throw new BusinessException("当前流程不在" + ("AI".equals(expectedType) ? "AI" : "视频") + "审批阶段");
        }
        if (!"WAITING_APPROVAL".equals(stage.getStageStatus())) {
            throw new BusinessException("当前阶段尚未完成，不能审批");
        }
        if ("VIDEO".equals(expectedType)) {
            InterviewVideoSession session = requireVideoSessionByProcessStage(process.getId(), stage.getId());
            if (!(StrUtil.equals(session.getSessionStatus(), "WAITING_APPROVAL") || StrUtil.equals(session.getSessionStatus(), "RECORDED"))) {
                throw new BusinessException("视频面试尚未结束，不能审批");
            }
        }
        claimProcessDecision(process, expectedType, "WAITING_APPROVAL");
        stage.setApproved(request.getApproved());
        stage.setApprovedHrUserId(request.getApproverUserId());
        stage.setApprovedHrName(request.getApproverName());
        stage.setStageStatus(request.getApproved() == 1 ? "PASSED" : "REJECTED");
        processStageMapper.updateById(stage);
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        if (request.getApproved() == 1) {
            advanceTemplateProcess(process, stage, request);
        } else {
            rejectTemplateProcess(process, stage, "HR审批不通过");
        }
        processMapper.updateById(process);
        updateCandidateStage(process);
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "AI".equals(expectedType) ? "APPROVE_AI" : "APPROVE_VIDEO", "INTERVIEW_PROCESS", String.valueOf(process.getId()), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO approveAiToVideo(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        if (isTemplateProcess(process)) {
            return decideTemplateStage(process, "AI", request);
        }
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "AI") || !StrUtil.equals(process.getStageStatus(), "WAITING_APPROVAL")) {
            throw new BusinessException("当前流程不在AI审批阶段");
        }
        claimProcessDecision(process, "AI", "WAITING_APPROVAL");
        if (request.getApproved() == 1) {
            process.setCurrentStage("VIDEO");
            process.setStageStatus("READY");
            process.setProcessStatusView("视频面");
        } else {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
            process.setProcessStatusView("已拒绝");
        }
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        processMapper.updateById(process);
        if (request.getApproved() == 1) {
            ensureVideoSession(processId, request.getApproverUserId(), request.getApproverName());
        }
        updateCandidateStage(process);
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "APPROVE_AI", "INTERVIEW_PROCESS", String.valueOf(processId), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO approveVideoToOnsite(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        if (isTemplateProcess(process)) {
            return decideTemplateStage(process, "VIDEO", request);
        }
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "VIDEO")) {
            throw new BusinessException("当前流程不在视频审批阶段");
        }
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (!(StrUtil.equals(session.getSessionStatus(), "WAITING_APPROVAL") || StrUtil.equals(session.getSessionStatus(), "RECORDED"))) {
            throw new BusinessException("视频面试尚未结束，不能审批");
        }
        claimProcessDecision(process, "VIDEO", "WAITING_APPROVAL");
        process.setVideoApproved(request.getApproved());
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        if (request.getApproved() == 1) {
            process.setCurrentStage("ONSITE");
            process.setStageStatus("READY");
            process.setProcessStatusView("线下面");
        } else {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
            process.setProcessStatusView("已拒绝");
        }
        processMapper.updateById(process);
        updateCandidateStage(process);
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "APPROVE_VIDEO", "INTERVIEW_PROCESS", String.valueOf(processId), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO decideOnsite(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "ONSITE")) {
            throw new BusinessException("当前流程不在线下面审批阶段");
        }
        claimProcessDecision(process, "ONSITE", null);
        process.setOnsiteApproved(request.getApproved());
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        if (request.getApproved() == 1) {
            process.setOverallStatus("PASSED");
            process.setStageStatus("PASSED");
            process.setProcessStatusView("已通过");
            syncToPendingOnboarding(process, request.getDepartmentId());
        } else {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
            process.setProcessStatusView("已拒绝");
        }
        processMapper.updateById(process);
        updateCandidateStage(process);
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "APPROVE_ONSITE", "INTERVIEW_PROCESS", String.valueOf(processId), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO terminateProcess(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        ensureInProgress(process);
        claimProcessDecision(process, null, null);
        process.setOverallStatus("TERMINATED");
        process.setStageStatus("TERMINATED");
        process.setProcessStatusView("已终止");
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        processMapper.updateById(process);
        updateCandidateStage(process);
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "TERMINATE_PROCESS", "INTERVIEW_PROCESS", String.valueOf(processId), "终止面试流程");
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO updateProcessRemark(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        process.setRemark(abbreviate(StrUtil.blankToDefault(request.getComment(), ""), 2000));
        processMapper.updateById(process);
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "UPDATE_PROCESS_REMARK", "INTERVIEW_PROCESS", String.valueOf(processId), "更新面试备注");
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public VideoSignalVO publishHrOffer(Long processId, VideoSignalRequest request) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (StrUtil.equals(session.getHrOfferSdp(), request.getOfferSdp())
                && StrUtil.equals(session.getSessionStatus(), "OFFER_PUBLISHED")) {
            return toVideoSignalVO(session);
        }
        session.setHrOfferSdp(request.getOfferSdp());
        session.setIntervieweeAnswerSdp(null);
        session.setHrIceCandidates(null);
        session.setIntervieweeIceCandidates(null);
        session.setEndTime(null);
        session.setRecordingPath(null);
        session.setRecordingFileName(null);
        session.setHrRecordingPath(null);
        session.setHrRecordingFileName(null);
        session.setIntervieweeRecordingPath(null);
        session.setIntervieweeRecordingFileName(null);
        session.setMergedRecordingPath(null);
        session.setMergedRecordingFileName(null);
        session.setSessionStatus("OFFER_PUBLISHED");
        videoSessionMapper.updateById(session);
        auditLogService.log(session.getApproverUserId(), session.getApproverName(), "HR_ADMIN", "INTERVIEW", "PUBLISH_VIDEO_OFFER", "VIDEO_SESSION", String.valueOf(session.getId()), session.getVideoSerialNo());
        return toVideoSignalVO(session);
    }

    @Override
    @Transactional
    public VideoSignalVO submitIntervieweeAnswer(Long processId, VideoSignalRequest request, Long intervieweeUserId, String intervieweeName) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (StrUtil.equals(session.getIntervieweeAnswerSdp(), request.getAnswerSdp())
                && StrUtil.equals(session.getSessionStatus(), "ANSWER_SUBMITTED")) {
            return toIntervieweeVideoSignalVO(session);
        }
        session.setIntervieweeAnswerSdp(request.getAnswerSdp());
        if (session.getIntervieweeJoinTime() == null) {
            session.setIntervieweeJoinTime(LocalDateTime.now());
        }
        session.setIntervieweeIceCandidates(null);
        session.setSessionStatus(canStartSynchronizedRecording(session) ? "RECORDING" : "ANSWER_SUBMITTED");
        videoSessionMapper.updateById(session);
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "SUBMIT_VIDEO_ANSWER", "VIDEO_SESSION", String.valueOf(session.getId()), session.getVideoSerialNo());
        return toIntervieweeVideoSignalVO(session);
    }

    @Override
    @Transactional
    public VideoSignalVO addHrIceCandidate(Long processId, VideoSignalRequest request) {
        String candidate = validateIceCandidate(request.getIceCandidate());
        for (int attempt = 0; attempt < ICE_APPEND_MAX_ATTEMPTS; attempt++) {
            InterviewVideoSession session = requireVideoSessionByProcess(processId);
            if (!isCurrentIceCandidate(session.getHrOfferSdp(), candidate)
                    || containsSignal(session.getHrIceCandidates(), candidate)) {
                return toVideoSignalVO(session);
            }
            String existing = session.getHrIceCandidates();
            String appended = appendIceCandidate(existing, candidate);
            LambdaUpdateWrapper<InterviewVideoSession> update = new LambdaUpdateWrapper<InterviewVideoSession>()
                    .eq(InterviewVideoSession::getId, session.getId())
                    .eq(existing != null, InterviewVideoSession::getHrIceCandidates, existing)
                    .isNull(existing == null, InterviewVideoSession::getHrIceCandidates)
                    .set(InterviewVideoSession::getHrIceCandidates, appended);
            if (videoSessionMapper.update(null, update) == 1) {
                session.setHrIceCandidates(appended);
                return toVideoSignalVO(session);
            }
        }
        throw new BusinessException("ICE候选更新冲突，请重试");
    }

    @Override
    @Transactional
    public VideoSignalVO addIntervieweeIceCandidate(Long processId, VideoSignalRequest request, Long intervieweeUserId, String intervieweeName) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        String candidate = validateIceCandidate(request.getIceCandidate());
        for (int attempt = 0; attempt < ICE_APPEND_MAX_ATTEMPTS; attempt++) {
            InterviewVideoSession session = requireVideoSessionByProcess(processId);
            if (!isCurrentIceCandidate(session.getIntervieweeAnswerSdp(), candidate)
                    || containsSignal(session.getIntervieweeIceCandidates(), candidate)) {
                return toIntervieweeVideoSignalVO(session);
            }
            String existing = session.getIntervieweeIceCandidates();
            String appended = appendIceCandidate(existing, candidate);
            LambdaUpdateWrapper<InterviewVideoSession> update = new LambdaUpdateWrapper<InterviewVideoSession>()
                    .eq(InterviewVideoSession::getId, session.getId())
                    .eq(existing != null, InterviewVideoSession::getIntervieweeIceCandidates, existing)
                    .isNull(existing == null, InterviewVideoSession::getIntervieweeIceCandidates)
                    .set(InterviewVideoSession::getIntervieweeIceCandidates, appended);
            if (videoSessionMapper.update(null, update) == 1) {
                session.setIntervieweeIceCandidates(appended);
                return toIntervieweeVideoSignalVO(session);
            }
        }
        throw new BusinessException("ICE候选更新冲突，请重试");
    }

    @Override
    public VideoSignalVO getVideoSignalState(Long processId) {
        return toVideoSignalVO(requireVideoSessionByProcess(processId));
    }

    @Override
    public VideoSignalVO getIntervieweeVideoSignalState(Long processId, Long intervieweeUserId) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        return toIntervieweeVideoSignalVO(requireVideoSessionByProcess(processId));
    }

    @Override
    public InterviewVideoSession getVideoSession(Long processId, Long processStageId) {
        return processStageId == null ? requireVideoSessionByProcess(processId) : requireVideoSessionForStage(processId, processStageId);
    }

    @Override
    @Transactional
    public InterviewVideoSession getDownloadableVideoSession(Long processId, Long processStageId) {
        InterviewVideoSession session = processStageId == null ? requireVideoSessionByProcess(processId) : requireVideoSessionForStage(processId, processStageId);
        if (videoMergeService.canMerge(session) && !isReadableFile(session.getMergedRecordingPath())) {
            prepareVideoMerge(session);
            videoSessionMapper.updateById(session);
            scheduleVideoMergeAndSummary(session.getId());
        }
        return session;
    }

    @Override
    @Transactional
    public InterviewVO retryVideoSummary(Long processId) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (StrUtil.equals(session.getSummaryStatus(), "FAILED_MERGE")) {
            if (!videoMergeService.canMerge(session)) {
                throw new BusinessException("缺少双方录像，无法重新合并");
            }
            prepareVideoMerge(session);
            videoSessionMapper.updateById(session);
            scheduleVideoMergeAndSummary(session.getId());
            return getProcess(processId);
        }
        if (!isReadableFile(StrUtil.blankToDefault(session.getMergedRecordingPath(), session.getRecordingPath()))) {
            throw new BusinessException("没有可处理的视频录制文件");
        }
        if (StrUtil.equals(session.getSummaryStatus(), "PROCESSING")) {
            throw new BusinessException("转写与会议概要正在生成，请稍候");
        }
        session.setAudioPath(null);
        session.setAudioFileName(null);
        session.setTranscriptText(null);
        session.setSummaryText(null);
        session.setSummaryStatus("PENDING");
        videoSessionMapper.updateById(session);
        runAfterCommit(() -> CompletableFuture.runAsync(() -> summarizeVideoSessionSafely(session.getId())));
        return getProcess(processId);
    }

    @Override
    @Transactional
    public VideoSignalVO uploadHrRecording(Long processId, String originalFileName, String contentType, MultipartFile file) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        VideoSignalVO vo = storeRecording(session, originalFileName, contentType, file, "hr");
        auditLogService.log(session.getApproverUserId(), session.getApproverName(), "HR_ADMIN", "INTERVIEW", "UPLOAD_RECORDING", "VIDEO_SESSION", String.valueOf(session.getId()), session.getRecordingFileName());
        return vo;
    }

    private VideoSignalVO storeRecording(InterviewVideoSession session, String originalFileName, String contentType, MultipartFile file, String role) {
        validateRecordingFile(originalFileName, contentType, file);
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            String ext = ".webm";
            String storedName = session.getVideoSerialNo() + "-" + role + ext;
            Path storedFile = UploadPaths.RECORDING_DIR.resolve(storedName).normalize().toAbsolutePath();
            if (!storedFile.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("录制文件路径非法");
            }
            file.transferTo(storedFile.toFile());
            s3ObjectStorageService.archiveIfEnabled(storedFile, "interview-recordings/" + storedName, "video/webm");
            if (StrUtil.equals(role, "hr")) {
                session.setHrRecordingPath(storedFile.toString());
                session.setHrRecordingFileName(storedName);
            } else {
                session.setIntervieweeRecordingPath(storedFile.toString());
                session.setIntervieweeRecordingFileName(storedName);
            }
            session.setRecordingPath(storedFile.toString());
            session.setRecordingFileName(storedName);
            if (videoMergeService.canMerge(session)) {
                prepareVideoMerge(session);
            } else if (!StrUtil.equalsAny(session.getSessionStatus(), "END_REQUESTED", "WAITING_APPROVAL")) {
                session.setSessionStatus("END_REQUESTED");
            }
            videoSessionMapper.updateById(session);
            if (videoMergeService.canMerge(session)) {
                scheduleVideoMergeAndSummary(session.getId());
            }
            return toVideoSignalVO(session);
        } catch (IOException ex) {
            throw new BusinessException("录制文件上传失败: " + ex.getMessage());
        }
    }

    private void validateRecordingFile(String originalFileName, String contentType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("录制文件不能为空");
        }
        if (file.getSize() > MAX_RECORDING_SIZE) {
            throw new BusinessException("录制文件不能超过100MB");
        }
        String fileName = Paths.get(StrUtil.blankToDefault(originalFileName, "recording.webm")).getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".webm")) {
            throw new BusinessException("仅支持WebM录制文件");
        }
        if (StrUtil.isNotBlank(contentType) && !ALLOWED_RECORDING_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("录制文件Content-Type不支持");
        }
    }

    @Override
    @Transactional
    public VideoSignalVO uploadIntervieweeRecording(Long processId, Long intervieweeUserId, String intervieweeName, String originalFileName, String contentType, MultipartFile file) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        VideoSignalVO vo = storeRecording(session, originalFileName, contentType, file, "interviewee");
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "UPLOAD_RECORDING", "VIDEO_SESSION", String.valueOf(vo.getSessionId()), vo.getRecordingFileName());
        return toIntervieweeVideoSignalVO(session);
    }

    @Override
    @Transactional
    public InterviewVO uploadAiExamRecording(Long processId, Long intervieweeUserId, String intervieweeName, String originalFileName, String contentType, MultipartFile file) {
        InterviewProcess process = requireIntervieweeProcess(processId, intervieweeUserId);
        validateRecordingFile(originalFileName, contentType, file);
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            String storedName = "ai-exam-" + processId + "-" + intervieweeUserId + "-" + System.currentTimeMillis() + ".webm";
            Path storedFile = UploadPaths.RECORDING_DIR.resolve(storedName).normalize().toAbsolutePath();
            if (!storedFile.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("AI面试录制文件路径非法");
            }
            file.transferTo(storedFile.toFile());
            s3ObjectStorageService.archiveIfEnabled(storedFile, "interview-recordings/" + storedName, "video/webm");
            if (isTemplateProcess(process)) {
                InterviewProcessStage stage = requireActiveProcessStage(process);
                if (!"AI".equals(stage.getStageType())) {
                    throw new BusinessException("当前流程不在AI面试阶段");
                }
                stage.setAiRecordingPath(storedFile.toString());
                stage.setAiRecordingFileName(storedName);
                processStageMapper.updateById(stage);
            } else {
                process.setAiRecordingPath(storedFile.toString());
                process.setAiRecordingFileName(storedName);
                processMapper.updateById(process);
            }
            auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "UPLOAD_AI_EXAM_RECORDING", "INTERVIEW_PROCESS", String.valueOf(processId), storedName);
            return toIntervieweeProcessVO(process);
        } catch (IOException ex) {
            throw new BusinessException("AI面试录制文件上传失败: " + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public InterviewVO reportAntiCheatEvent(AntiCheatEventRequest request, Long intervieweeUserId, String intervieweeName) {
        InterviewProcess process = requireIntervieweeProcess(request.getProcessId(), intervieweeUserId);
        String eventType = StrUtil.trim(request.getEventType());
        if (!ALLOWED_ANTI_CHEAT_EVENTS.contains(eventType)) {
            throw new BusinessException("不支持的反作弊事件类型");
        }
        boolean switchEvent = isSwitchEvent(eventType);
        if (switchEvent && isActiveAiStage(process)) {
            int count = Objects.requireNonNullElse(process.getAntiCheatSwitchCount(), 0) + 1;
            int limit = Math.max(Objects.requireNonNullElse(process.getAntiCheatSwitchLimit(), 5), 1);
            process.setAntiCheatSwitchCount(count);
            if (count >= limit) {
                if (isTemplateProcess(process)) {
                    setTemplateStageStatus(process, requireActiveProcessStage(process), "WAITING_APPROVAL");
                } else {
                    process.setStageStatus("WAITING_APPROVAL");
                    process.setProcessStatusView("切屏超限待人工审批");
                }
                updateCandidateStage(process);
                auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "ANTI_CHEAT_MANUAL_REVIEW", "INTERVIEW_PROCESS", String.valueOf(request.getProcessId()), "切屏" + count + "次达到阈值" + limit + "，转HR人工审批");
            }
            processMapper.updateById(process);
        }
        String detail = StrUtil.blankToDefault(request.getDetail(), "") + " eventType=" + eventType;
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "ANTI_CHEAT_" + eventType, "INTERVIEW_PROCESS", String.valueOf(request.getProcessId()), abbreviate(detail));
        return toIntervieweeProcessVO(process);
    }

    private boolean isSwitchEvent(String eventType) {
        return Set.of("FULLSCREEN_EXIT", "TAB_HIDDEN", "WINDOW_BLUR").contains(StrUtil.blankToDefault(eventType, ""));
    }

    private boolean isActiveAiStage(InterviewProcess process) {
        if (!StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            return false;
        }
        if (!isTemplateProcess(process)) {
            return StrUtil.equals(process.getCurrentStage(), "AI") && StrUtil.equals(process.getStageStatus(), "IN_PROGRESS");
        }
        InterviewProcessStage stage = requireActiveProcessStage(process);
        return "AI".equals(stage.getStageType()) && "IN_PROGRESS".equals(stage.getStageStatus());
    }

    private void syncToPendingOnboarding(InterviewProcess process, Long selectedDepartmentId) {
        RecruitmentCandidate candidate = requireRecruitmentCandidate(process.getRecruitmentCandidateId());
        RecruitmentJob job = requireRecruitmentJob(process.getJobId());
        Long departmentId = job.getDepartmentId() == null ? selectedDepartmentId : job.getDepartmentId();
        if (departmentId == null) {
            throw new BusinessException("岗位尚未关联部门，请选择入职部门后再通过最终审批");
        }
        Department department = departmentMapper.selectById(departmentId);
        if (department == null || !Objects.equals(department.getStatus(), 1)) {
            throw new BusinessException("入职部门不存在或已停用，请重新选择");
        }
        Employee existing = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getSourceCandidateId, candidate.getId())
                .last("LIMIT 1"));
        Employee employee = existing == null ? new Employee() : existing;
        if (existing == null) {
            employee.setEmployeeCode("PENDING-" + candidate.getId());
        }
        employee.setFullName(candidate.getFullName());
        employee.setIdCardNo(StrUtil.blankToDefault(candidate.getIdCardNo(), "PENDING-ID-" + candidate.getId()));
        employee.setMobilePhone(candidate.getMobilePhone());
        employee.setEmail(candidate.getEmail());
        employee.setRecruitmentMajor(candidate.getMajor());
        employee.setPositionName(job.getJobTitle());
        employee.setDepartmentId(department.getId());
        employee.setBankAccountNo(StrUtil.blankToDefault(employee.getBankAccountNo(), "PENDING" + candidate.getId()));
        employee.setBankName(StrUtil.blankToDefault(employee.getBankName(), "待补充"));
        employee.setHireDate(LocalDate.now());
        employee.setEmploymentStatus(EmploymentStatus.PENDING_ONBOARDING.getCode());
        employee.setSourceCandidateId(candidate.getId());
        employee.setInterviewStageStatus("线下面");
        employee.setSourceChannel("RECRUITMENT_INTERVIEW");
        employee.setNotes("面试流程通过，待入职同步生成");
        if (existing == null) {
            employeeMapper.insert(employee);
        } else {
            employeeMapper.updateById(employee);
        }
    }

    private void updateCandidateStage(InterviewProcess process) {
        RecruitmentCandidate candidate = requireRecruitmentCandidate(process.getRecruitmentCandidateId());
        candidate.setInterviewStageStatus(StrUtil.blankToDefault(process.getProcessStatusView(), "简历待查"));
        if (StrUtil.equals(process.getOverallStatus(), "REJECTED")) {
            candidate.setApplicationStatus("REJECTED");
        } else if (StrUtil.equals(process.getOverallStatus(), "TERMINATED")) {
            candidate.setApplicationStatus("TERMINATED");
        }
        recruitmentCandidateMapper.updateById(candidate);
    }

    private InterviewKnowledgeBase requireKnowledgeBase(Long id) {
        InterviewKnowledgeBase entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) throw new BusinessException("知识库不存在: " + id);
        return entity;
    }

    private InterviewKnowledgeItem requireKnowledgeItem(Long id) {
        InterviewKnowledgeItem entity = knowledgeItemMapper.selectById(id);
        if (entity == null) throw new BusinessException("知识项不存在: " + id);
        return entity;
    }

    private InterviewJobKnowledgeWeight requireJobKnowledgeWeight(Long id) {
        InterviewJobKnowledgeWeight entity = jobKnowledgeWeightMapper.selectById(id);
        if (entity == null) throw new BusinessException("岗位知识权重不存在: " + id);
        return entity;
    }

    private InterviewLlmConfig requireLlmConfig(Long id) {
        InterviewLlmConfig entity = llmConfigMapper.selectById(id);
        if (entity == null) throw new BusinessException("LLM配置不存在: " + id);
        return entity;
    }

    private InterviewProcessTemplate requireProcessTemplate(Long id) {
        InterviewProcessTemplate entity = processTemplateMapper.selectById(id);
        if (entity == null) throw new BusinessException("流程模板不存在: " + id);
        return entity;
    }

    private InterviewProcessTemplate requireActiveProcessTemplate(Long id) {
        InterviewProcessTemplate template = requireProcessTemplate(id);
        if (!Objects.equals(template.getStatus(), 1)) {
            throw new BusinessException("流程模板已停用，不能发起新面试");
        }
        if (listTemplateStages(id).isEmpty()) {
            throw new BusinessException("流程模板没有可用阶段");
        }
        return template;
    }

    private List<InterviewProcessTemplateStage> listTemplateStages(Long templateId) {
        return processTemplateStageMapper.selectList(new LambdaQueryWrapper<InterviewProcessTemplateStage>()
                .eq(InterviewProcessTemplateStage::getTemplateId, templateId)
                .orderByAsc(InterviewProcessTemplateStage::getSequenceNo)
                .orderByAsc(InterviewProcessTemplateStage::getId));
    }

    private boolean isTemplateProcess(InterviewProcess process) {
        return process.getTemplateId() != null;
    }

    private List<InterviewProcessStage> listProcessStages(Long processId) {
        return processStageMapper.selectList(new LambdaQueryWrapper<InterviewProcessStage>()
                .eq(InterviewProcessStage::getProcessId, processId)
                .orderByAsc(InterviewProcessStage::getSequenceNo)
                .orderByAsc(InterviewProcessStage::getId));
    }

    private InterviewProcessStage requireActiveProcessStage(InterviewProcess process) {
        InterviewProcessStage stage = processStageMapper.selectOne(new LambdaQueryWrapper<InterviewProcessStage>()
                .eq(InterviewProcessStage::getProcessId, process.getId())
                .in(InterviewProcessStage::getStageStatus, List.of("IN_PROGRESS", "READY", "UPLOADING", "WAITING_APPROVAL"))
                .orderByAsc(InterviewProcessStage::getSequenceNo)
                .last("LIMIT 1"));
        if (stage == null) {
            throw new BusinessException("流程没有可执行的当前阶段");
        }
        return stage;
    }

    private void validateTemplateStages(List<InterviewProcessTemplateStageRequest> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new BusinessException("请至少添加一个面试阶段");
        }
        for (InterviewProcessTemplateStageRequest stage : stages) {
            String stageType = normalizeTemplateStageType(stage.getStageType());
            if ("AI".equals(stageType) && stage.getKnowledgeBaseId() == null) {
                throw new BusinessException("AI面试阶段必须选择知识库");
            }
            if ("AI".equals(stageType)) {
                InterviewKnowledgeBase knowledgeBase = requireKnowledgeBase(stage.getKnowledgeBaseId());
                if (!Objects.equals(knowledgeBase.getStatus(), 1)) {
                    throw new BusinessException("AI面试阶段只能选择启用的知识库");
                }
            }
        }
    }

    private String normalizeTemplateStageType(String stageType) {
        if (StrUtil.equalsIgnoreCase(stageType, "AI")) {
            return "AI";
        }
        if (StrUtil.equalsAnyIgnoreCase(stageType, "VIDEO", "HUMAN", "HUMAN_VIDEO")) {
            return "VIDEO";
        }
        throw new BusinessException("流程阶段类型仅支持 AI 或 VIDEO");
    }

    private void cloneTemplateStages(InterviewProcess process, List<InterviewProcessTemplateStage> templateStages) {
        for (int index = 0; index < templateStages.size(); index++) {
            InterviewProcessTemplateStage source = templateStages.get(index);
            InterviewProcessStage stage = new InterviewProcessStage();
            stage.setProcessId(process.getId());
            stage.setTemplateStageId(source.getId());
            stage.setStageName(source.getStageName());
            stage.setStageType(source.getStageType());
            stage.setKnowledgeBaseId(source.getKnowledgeBaseId());
            stage.setSequenceNo(index + 1);
            stage.setStageStatus(index == 0 ? ("AI".equals(source.getStageType()) ? "IN_PROGRESS" : "READY") : "PENDING");
            processStageMapper.insert(stage);
        }
    }

    private InterviewProcess requireProcess(Long id) {
        InterviewProcess entity = processMapper.selectById(id);
        if (entity == null) throw new BusinessException("面试流程不存在: " + id);
        return entity;
    }

    private InterviewProcess requireIntervieweeProcess(Long processId, Long intervieweeUserId) {
        InterviewProcess process = requireProcess(processId);
        if (!Objects.equals(process.getIntervieweeUserId(), intervieweeUserId)) {
            throw new BusinessException("无权访问该面试流程");
        }
        return process;
    }

    private InterviewVideoSession requireVideoSessionByProcess(Long processId) {
        InterviewProcess process = requireProcess(processId);
        if (isTemplateProcess(process)) {
            InterviewProcessStage activeStage = requireActiveProcessStage(process);
            if (!"VIDEO".equals(activeStage.getStageType())) {
                throw new BusinessException("当前流程不在视频面试阶段");
            }
            return requireVideoSessionByProcessStage(processId, activeStage.getId());
        }
        InterviewVideoSession entity = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .isNull(InterviewVideoSession::getProcessStageId)
                .last("LIMIT 1"));
        if (entity == null) throw new BusinessException("视频面试会话不存在");
        return entity;
    }

    private InterviewVideoSession requireVideoSessionByProcessStage(Long processId, Long processStageId) {
        InterviewVideoSession entity = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .eq(InterviewVideoSession::getProcessStageId, processStageId)
                .last("LIMIT 1"));
        if (entity == null) throw new BusinessException("视频面试会话不存在");
        return entity;
    }

    private InterviewVideoSession requireVideoSessionForStage(Long processId, Long processStageId) {
        InterviewProcessStage stage = processStageMapper.selectById(processStageId);
        if (stage == null || !Objects.equals(stage.getProcessId(), processId) || !"VIDEO".equals(stage.getStageType())) {
            throw new BusinessException("视频面试阶段不存在");
        }
        return requireVideoSessionByProcessStage(processId, processStageId);
    }

    private InterviewVideoSession ensureVideoSession(Long processId, Long approverUserId, String approverName) {
        InterviewVideoSession existing = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .isNull(InterviewVideoSession::getProcessStageId)
                .last("LIMIT 1"));
        if (existing != null) {
            resetVideoSession(existing, approverUserId, approverName);
            videoSessionMapper.updateById(existing);
            return existing;
        }
        InterviewVideoSession session = new InterviewVideoSession();
        session.setProcessId(processId);
        session.setVideoSerialNo("VID-" + UUID.randomUUID());
        session.setVideoJoinLink("/interview/interviewee?processId=" + processId + "&serial=" + session.getVideoSerialNo());
        session.setApproverUserId(approverUserId);
        session.setApproverName(approverName);
        session.setSessionStatus("CREATED");
        videoSessionMapper.insert(session);
        return session;
    }

    private InterviewVideoSession ensureVideoSession(Long processId, Long processStageId, Long approverUserId, String approverName) {
        InterviewVideoSession existing = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .eq(InterviewVideoSession::getProcessStageId, processStageId)
                .last("LIMIT 1"));
        if (existing != null) {
            if (StrUtil.equals(existing.getSessionStatus(), "CREATED")) {
                existing.setApproverUserId(approverUserId);
                existing.setApproverName(approverName);
                videoSessionMapper.updateById(existing);
            }
            return existing;
        }
        InterviewVideoSession session = new InterviewVideoSession();
        session.setProcessId(processId);
        session.setProcessStageId(processStageId);
        session.setVideoSerialNo("VID-" + UUID.randomUUID());
        session.setVideoJoinLink("/interview/interviewee?processId=" + processId + "&serial=" + session.getVideoSerialNo());
        session.setApproverUserId(approverUserId);
        session.setApproverName(approverName);
        session.setSessionStatus("CREATED");
        videoSessionMapper.insert(session);
        return session;
    }

    private void resetVideoSession(InterviewVideoSession session, Long approverUserId, String approverName) {
        session.setVideoSerialNo("VID-" + UUID.randomUUID());
        session.setVideoJoinLink("/interview/interviewee?processId=" + session.getProcessId() + "&serial=" + session.getVideoSerialNo());
        session.setApproverUserId(approverUserId);
        session.setApproverName(approverName);
        session.setIntervieweeJoinTime(null);
        session.setHrJoinTime(null);
        session.setStartTime(null);
        session.setEndTime(null);
        session.setRecordingEndRequestedAt(null);
        session.setRecordingPath(null);
        session.setRecordingFileName(null);
        session.setHrRecordingPath(null);
        session.setHrRecordingFileName(null);
        session.setIntervieweeRecordingPath(null);
        session.setIntervieweeRecordingFileName(null);
        session.setMergedRecordingPath(null);
        session.setMergedRecordingFileName(null);
        session.setAudioPath(null);
        session.setAudioFileName(null);
        session.setTranscriptText(null);
        session.setSummaryText(null);
        session.setSummaryStatus(null);
        session.setHrOfferSdp(null);
        session.setIntervieweeAnswerSdp(null);
        session.setHrIceCandidates(null);
        session.setIntervieweeIceCandidates(null);
        session.setSessionStatus("CREATED");
    }

    private void ensureInProgress(InterviewProcess process) {
        if (!StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            throw new BusinessException("当前流程已结束，不能重复审批或回退");
        }
    }

    private void claimProcessDecision(InterviewProcess process, String expectedStage, String expectedStageStatus) {
        LambdaUpdateWrapper<InterviewProcess> claim = new LambdaUpdateWrapper<InterviewProcess>()
                .eq(InterviewProcess::getId, process.getId())
                .eq(InterviewProcess::getOverallStatus, "IN_PROGRESS")
                .eq(StrUtil.isNotBlank(expectedStage), InterviewProcess::getCurrentStage, expectedStage)
                .eq(StrUtil.isNotBlank(expectedStageStatus), InterviewProcess::getStageStatus, expectedStageStatus)
                .set(InterviewProcess::getOverallStatus, "DECISION_PROCESSING");
        if (processMapper.update(null, claim) != 1) {
            throw new BusinessException("流程状态已变化，请刷新后重试");
        }
    }

    private RecruitmentCandidate requireRecruitmentCandidate(Long id) {
        RecruitmentCandidate entity = recruitmentCandidateMapper.selectById(id);
        if (entity == null) throw new BusinessException("招聘候选人不存在: " + id);
        return entity;
    }

    private RecruitmentJob requireRecruitmentJob(Long id) {
        RecruitmentJob entity = recruitmentJobMapper.selectById(id);
        if (entity == null) throw new BusinessException("招聘岗位不存在: " + id);
        return entity;
    }

    private InterviewJobKnowledgeWeight pickKnowledgeWeight(InterviewProcess process) {
        List<InterviewJobKnowledgeWeight> weights = jobKnowledgeWeightMapper.selectList(new LambdaQueryWrapper<InterviewJobKnowledgeWeight>()
                .eq(InterviewJobKnowledgeWeight::getJobId, process.getJobId())
                .orderByDesc(InterviewJobKnowledgeWeight::getWeight)
                .orderByAsc(InterviewJobKnowledgeWeight::getId));
        if (weights.isEmpty()) {
            return null;
        }
        List<InterviewJobKnowledgeWeight> validWeights = weights.stream()
                .filter(item -> Objects.requireNonNullElse(item.getWeight(), 0) > 0)
                .toList();
        if (validWeights.isEmpty()) {
            return weights.get(0);
        }
        List<InterviewAiRecord> existingRecords = aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessId, process.getId()));
        InterviewJobKnowledgeWeight selected = null;
        double selectedRatio = Double.MAX_VALUE;
        int selectedCount = Integer.MAX_VALUE;
        for (InterviewJobKnowledgeWeight item : validWeights) {
            int usedCount = (int) existingRecords.stream()
                    .filter(record -> Objects.equals(record.getKnowledgeBaseId(), item.getKnowledgeBaseId()))
                    .count();
            double ratio = usedCount / (double) item.getWeight();
            if (selected == null || ratio < selectedRatio || (ratio == selectedRatio && usedCount < selectedCount)) {
                selected = item;
                selectedRatio = ratio;
                selectedCount = usedCount;
            }
        }
        return selected;
    }

    private void setTemplateStageStatus(InterviewProcess process, InterviewProcessStage stage, String status) {
        stage.setStageStatus(status);
        processStageMapper.updateById(stage);
        process.setCurrentStage(stage.getStageType());
        process.setStageStatus(status);
        process.setProcessStatusView(stageStatusView(stage.getStageName(), status));
    }

    private void advanceTemplateProcess(InterviewProcess process, InterviewProcessStage completedStage, InterviewDecisionRequest request) {
        InterviewProcessStage nextStage = processStageMapper.selectOne(new LambdaQueryWrapper<InterviewProcessStage>()
                .eq(InterviewProcessStage::getProcessId, process.getId())
                .gt(InterviewProcessStage::getSequenceNo, completedStage.getSequenceNo())
                .orderByAsc(InterviewProcessStage::getSequenceNo)
                .last("LIMIT 1"));
        if (nextStage == null) {
            process.setOverallStatus("PASSED");
            process.setStageStatus("PASSED");
            process.setProcessStatusView("已通过");
            syncToPendingOnboarding(process, request.getDepartmentId());
            return;
        }
        String nextStatus = "AI".equals(nextStage.getStageType()) ? "IN_PROGRESS" : "READY";
        nextStage.setStageStatus(nextStatus);
        processStageMapper.updateById(nextStage);
        process.setCurrentStage(nextStage.getStageType());
        process.setStageStatus(nextStatus);
        process.setProcessStatusView(stageStatusView(nextStage.getStageName(), nextStatus));
        if ("AI".equals(nextStage.getStageType())) {
            runAfterCommit(() -> CompletableFuture.runAsync(() -> generateInitialQuestionSafely(process.getId())));
        } else {
            ensureVideoSession(process.getId(), nextStage.getId(), request.getApproverUserId(), request.getApproverName());
        }
    }

    private void rejectTemplateProcess(InterviewProcess process, InterviewProcessStage stage, String reason) {
        stage.setStageStatus("REJECTED");
        processStageMapper.updateById(stage);
        process.setOverallStatus("REJECTED");
        process.setStageStatus("REJECTED");
        process.setProcessStatusView(stage.getStageName() + "未通过");
        auditLogService.log(process.getIntervieweeUserId(), "面试者", "INTERVIEWEE", "INTERVIEW", "TEMPLATE_STAGE_REJECT", "INTERVIEW_PROCESS", String.valueOf(process.getId()), stage.getStageName() + "：" + reason);
    }

    private String stageStatusView(String stageName, String stageStatus) {
        String name = StrUtil.blankToDefault(stageName, "面试阶段");
        return switch (stageStatus) {
            case "READY", "IN_PROGRESS" -> name;
            case "UPLOADING" -> name + "录制上传中";
            case "WAITING_APPROVAL" -> name + "待审批";
            case "PASSED" -> name + "已通过";
            case "REJECTED" -> name + "未通过";
            default -> name;
        };
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private synchronized void generateInitialQuestionSafely(Long processId) {
        try {
            generateNextQuestion(requireProcess(processId));
        } catch (Exception ex) {
            saveQuestionGenerationFailure(processId, ex);
        }
    }

    private synchronized void saveQuestionGenerationFailure(Long processId, Exception ex) {
        InterviewProcess process = requireProcess(processId);
        InterviewProcessStage stage = isTemplateProcess(process) ? requireActiveProcessStage(process) : null;
        InterviewAiRecord record = new InterviewAiRecord();
        record.setProcessId(processId);
        record.setProcessStageId(stage == null ? null : stage.getId());
        record.setKnowledgePoint("题目生成异常");
        record.setQuestionContent("AI题目生成失败，请联系管理员检查知识库权重、LLM配置或接口状态。错误：" + abbreviate(ex.getMessage()));
        record.setSequenceNo(nextSequence(processId, stage == null ? null : stage.getId()));
        aiRecordMapper.insert(record);
    }

    private synchronized void generateNextQuestion(InterviewProcess process) {
        InterviewProcessStage stage = isTemplateProcess(process) ? requireActiveProcessStage(process) : null;
        Long knowledgeBaseId = stage == null ? null : stage.getKnowledgeBaseId();
        InterviewJobKnowledgeWeight weight = knowledgeBaseId == null ? pickKnowledgeWeight(process) : null;
        InterviewAiRecord record = new InterviewAiRecord();
        record.setProcessId(process.getId());
        record.setProcessStageId(stage == null ? null : stage.getId());
        record.setKnowledgeBaseId(knowledgeBaseId == null ? (weight == null ? null : weight.getKnowledgeBaseId()) : knowledgeBaseId);
        record.setKnowledgePoint(loadKnowledgeBaseName(record.getKnowledgeBaseId()));
        record.setQuestionContent(callLlmQuestion(record.getKnowledgePoint(), loadKnowledgeMaterials(record.getKnowledgeBaseId()), loadJobRequirements(process)));
        record.setSequenceNo(nextSequence(process.getId(), stage == null ? null : stage.getId()));
        aiRecordMapper.insert(record);
    }

    private String loadKnowledgeBaseName(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return "通用沟通";
        }
        InterviewKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        return base == null ? "通用沟通" : base.getKnowledgeBaseName();
    }

    private String loadKnowledgeMaterials(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return "";
        }
        List<InterviewKnowledgeItem> items = knowledgeItemMapper.selectList(new LambdaQueryWrapper<InterviewKnowledgeItem>()
                .eq(InterviewKnowledgeItem::getKnowledgeBaseId, knowledgeBaseId)
                .eq(InterviewKnowledgeItem::getStatus, 1)
                .orderByAsc(InterviewKnowledgeItem::getId));
        return items.stream()
                .map(item -> "知识点：" + item.getKnowledgePoint() + "\n材料：" + item.getKnowledgeContent())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private String loadJobRequirements(InterviewProcess process) {
        RecruitmentJob job = process == null || process.getJobId() == null ? null : recruitmentJobMapper.selectById(process.getJobId());
        return job == null ? "" : StrUtil.blankToDefault(job.getRequirements(), "");
    }

    private String callLlmQuestion(String topic, String materials, String jobRequirements) {
        InterviewLlmConfig config = requireActiveLlmConfig("INTERVIEWER");
        String prompt = "你是一名AI面试官，请根据用户提供的材料生成一道中文面试题。只输出题目内容，不要评分，不要评价，不要输出答案。";
        String userPrompt = "知识库主题：" + topic + "\n\n知识库材料：\n" + StrUtil.blankToDefault(materials, "无补充材料")
                + "\n\n请只基于上述知识库材料生成一道面试题。题目必须能从材料中找到考察依据，可以对知识点原句做自然、清晰的语义改写，但不要引入材料外的知识点，不要输出解释。";
        String question = callOpenAiChat(config, prompt + "\n岗位要求：\n" + StrUtil.blankToDefault(jobRequirements, "未填写")
                        + "\n你必须根据用户提供的知识库材料出题，并结合岗位要求判断考察重点。允许自然表达和语义修饰，但考察依据必须来自材料和岗位要求。本指令优先于模型配置中的打分、评价或只返回分数类要求。", userPrompt)
                .replace("\n", " ")
                .trim();
        if (StrUtil.isBlank(question)) {
            throw new BusinessException("LLM未返回面试题内容");
        }
        return question;
    }

    private synchronized void generateFollowUpQuestion(InterviewProcess process, InterviewAiRecord previousRecord,
                                                       String previousAnswer, String nextQuestion) {
        String followUpQuestion = StrUtil.isBlank(nextQuestion)
                ? callLlmFollowUpQuestion(previousRecord, previousAnswer, loadKnowledgeMaterials(previousRecord.getKnowledgeBaseId()), loadJobRequirements(process))
                : nextQuestion;
        InterviewAiRecord record = new InterviewAiRecord();
        record.setProcessId(process.getId());
        record.setProcessStageId(previousRecord.getProcessStageId());
        record.setKnowledgeBaseId(previousRecord.getKnowledgeBaseId());
        record.setKnowledgePoint(previousRecord.getKnowledgePoint());
        record.setQuestionContent(followUpQuestion.replace("\n", " ").trim());
        record.setSequenceNo(nextSequence(process.getId(), previousRecord.getProcessStageId()));
        aiRecordMapper.insert(record);
    }

    private String callLlmFollowUpQuestion(InterviewAiRecord previousRecord, String previousAnswer, String materials, String jobRequirements) {
        InterviewLlmConfig config = requireActiveLlmConfig("INTERVIEWER");
        String systemPrompt = "你是一名严谨、友善的技术面试官。请针对候选人上一回答中不完整、含糊或错误的部分，生成一道中文深入追问题。"
                + "追问必须与上一题属于同一知识域，并能由给定知识库材料和岗位要求支持。只输出一道问题，不要评分、解释或答案。";
        String userPrompt = "知识域：" + StrUtil.blankToDefault(previousRecord.getKnowledgePoint(), "通用沟通")
                + "\n\n知识库材料：\n" + StrUtil.blankToDefault(materials, "无补充材料")
                + "\n\n岗位要求：\n" + StrUtil.blankToDefault(jobRequirements, "未填写")
                + "\n\n上一题：" + previousRecord.getQuestionContent()
                + "\n\n候选人回答：" + StrUtil.blankToDefault(previousAnswer, "未回答");
        String question = callOpenAiChat(config, systemPrompt, userPrompt).replace("\n", " ").trim();
        if (StrUtil.isBlank(question)) {
            throw new BusinessException("LLM未返回追问题目内容");
        }
        return question;
    }

    private LlmEvaluation callLlmEvaluation(String question, String answer, String topic, String materials, String jobRequirements, String role, boolean needNextQuestion) {
        return callLlmEvaluation(question, answer, topic, materials, jobRequirements, role, needNextQuestion, null);
    }

    private LlmEvaluation callLlmEvaluation(String question, String answer, String topic, String materials, String jobRequirements, String role, boolean needNextQuestion, Consumer<String> chunkConsumer) {
        InterviewLlmConfig config = requireActiveLlmConfig(role);
        String basePrompt = StrUtil.blankToDefault(config.getScoringRulePrompt(), config.getPromptTemplate())
                .replace("{topic}", StrUtil.blankToDefault(topic, "通用沟通"));
        if (StrUtil.isBlank(basePrompt)) {
            basePrompt = "请作为面试评分模型，基于知识库材料评价面试者回答。";
        }
        String userPrompt = "知识库主题：" + StrUtil.blankToDefault(topic, "通用沟通")
                + "\n\n知识库材料：\n" + StrUtil.blankToDefault(materials, "无补充材料")
                + "\n\n当前问题：" + StrUtil.blankToDefault(question, "未提供")
                + "\n\n面试者回答：\n" + StrUtil.blankToDefault(answer, "");

        if (!needNextQuestion) {
            String scorerPrompt = basePrompt + "\n岗位要求：\n" + StrUtil.blankToDefault(jobRequirements, "未填写")
                    + "\n请严格基于上述知识库材料、岗位要求、当前问题和面试者回答评分。只返回一个整数分数，不输出解释。";
            String response = callOpenAiChat(config, scorerPrompt, userPrompt);
            return new LlmEvaluation(parseScore(response), "", "");
        }

        String systemPrompt = basePrompt + "\n岗位要求：\n" + StrUtil.blankToDefault(jobRequirements, "未填写")
                + "\n请严格基于上述知识库材料、岗位要求、当前问题和面试者回答完成评价。"
                + "\n输出必须包含三部分：第一行只写整数分数；第二行写不少于20字的中文评价，评价要反馈回答是否完整、哪里正确或遗漏；第三行写下一道面试题。"
                + "\n第三行的下一题必须是针对当前回答缺口的深入追问，保持当前知识库主题，不得切换知识域；可以自然改写，但不能引入材料和岗位要求外的知识点。"
                + "\n本格式要求优先于旧配置中的'只返回整数'类要求，不能只输出分数，也不能只输出问题。";
        String response = chunkConsumer == null ? callOpenAiChat(config, systemPrompt, userPrompt) : callOpenAiChatStream(config, systemPrompt, userPrompt, chunkConsumer);
        return parseEvaluation(response);
    }

    private int parseScore(String response) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(StrUtil.blankToDefault(response, ""));
        if (!matcher.find()) {
            throw new BusinessException("LLM未返回有效分数");
        }
        try {
            long score = Long.parseLong(matcher.group());
            if (score < 0 || score > 100) {
                throw new BusinessException("LLM返回的分数超出有效范围");
            }
            return (int) score;
        } catch (NumberFormatException ex) {
            throw new BusinessException("LLM返回的分数超出有效范围");
        }
    }

    private int normalizeScore(long score) {
        return (int) Math.max(0, Math.min(100, score));
    }

    private LlmEvaluation parseEvaluation(String response) {
        int score = parseScore(response);
        String normalized = StrUtil.blankToDefault(response, "").replace("\r", "").trim();
        String comment = extractLabeledText(normalized, "评价", "下一题");
        String nextQuestion = extractLabeledText(normalized, "下一题", null);
        if (StrUtil.isBlank(comment)) {
            String[] lines = normalized.split("\n");
            comment = lines.length > 1 ? lines[1].replaceFirst("^\\s*评价[：:]\\s*", "").trim() : "本次回答已完成评分，但模型未返回详细评价。";
        }
        if (StrUtil.isBlank(nextQuestion)) {
            String[] lines = normalized.split("\n");
            nextQuestion = lines.length > 2 ? lines[2].replaceFirst("^\\s*下一题[：:]\\s*", "").trim() : "";
        }
        return new LlmEvaluation(score, comment, nextQuestion);
    }

    private String extractLabeledText(String text, String startLabel, String endLabel) {
        String startRegex = java.util.regex.Pattern.quote(startLabel) + "[：:]";
        java.util.regex.Pattern pattern = endLabel == null
                ? java.util.regex.Pattern.compile(startRegex + "([\\s\\S]*)")
                : java.util.regex.Pattern.compile(startRegex + "([\\s\\S]*?)(?:" + java.util.regex.Pattern.quote(endLabel) + "[：:]|$)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private int callLlmScore(String answer, String topic, String role) {
        InterviewLlmConfig config = requireActiveLlmConfig(role);
        String prompt = StrUtil.blankToDefault(config.getScoringRulePrompt(), config.getPromptTemplate())
                .replace("{topic}", topic);
        if (StrUtil.isBlank(prompt)) {
            prompt = "请作为评分模型，围绕主题" + topic + "对回答评分。只返回一个整数分数，不要输出解释。";
        }
        String response = callOpenAiChat(config, prompt + "\n只返回一个整数分数，不限制分数上限，由你的评分标准决定。", answer);
        return parseScore(response);
    }

    private void prepareVideoMerge(InterviewVideoSession session) {
        session.setSessionStatus("RECORDED");
        session.setSummaryStatus("PENDING_MERGE");
        session.setSummaryText(null);
        markVideoWaitingApproval(session.getProcessId());
    }

    private void scheduleVideoMergeAndSummary(Long sessionId) {
        runAfterCommit(() -> CompletableFuture.runAsync(() -> mergeAndSummarizeVideoSessionSafely(sessionId)));
    }

    private void mergeAndSummarizeVideoSessionSafely(Long sessionId) {
        BusinessException lastFailure = null;
        for (int attempt = 1; attempt <= VIDEO_MERGE_MAX_ATTEMPTS; attempt++) {
            InterviewVideoSession session = videoSessionMapper.selectById(sessionId);
            if (session == null || !videoMergeService.canMerge(session)) {
                markVideoMergeFailed(sessionId, "录像文件不完整，无法合并");
                return;
            }
            try {
                videoMergeService.mergeRecordings(session);
                session.setSummaryStatus("PENDING");
                videoSessionMapper.updateById(session);
                summarizeVideoSessionSafely(sessionId);
                return;
            } catch (BusinessException ex) {
                lastFailure = ex;
            }
        }
        String detail = lastFailure == null ? "未知错误" : abbreviate(lastFailure.getMessage(), 240);
        markVideoMergeFailed(sessionId, detail);
    }

    private void markVideoMergeFailed(Long sessionId, String detail) {
        InterviewVideoSession session = videoSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        session.setSessionStatus("RECORDED");
        session.setSummaryStatus("FAILED_MERGE");
        session.setSummaryText("录像合并已自动重试3次仍失败。原始录像已保留，审批流程不受影响。原因：" + abbreviate(detail, 240));
        videoSessionMapper.updateById(session);
    }

    private void summarizeVideoSessionSafely(Long sessionId) {
        InterviewVideoSession session = videoSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.setSummaryStatus("PROCESSING");
            videoSessionMapper.updateById(session);
            videoMergeService.extractAudio(session);
            videoSessionMapper.updateById(session);
            String transcript;
            if (videoMergeService.canMerge(session)) {
                String hrAudioPath = videoMergeService.extractSpeakerAudio(session, "hr");
                String intervieweeAudioPath = videoMergeService.extractSpeakerAudio(session, "interviewee");
                String hrTranscript = callAudioTranscription(hrAudioPath);
                String intervieweeTranscript = callAudioTranscription(intervieweeAudioPath);
                if (StrUtil.isBlank(hrTranscript) && StrUtil.isBlank(intervieweeTranscript)) {
                    throw new BusinessException("阿里云语音转文字未返回识别文本");
                }
                transcript = "面试官：" + StrUtil.blankToDefault(hrTranscript, "（未识别到语音）")
                        + "\n候选人：" + StrUtil.blankToDefault(intervieweeTranscript, "（未识别到语音）");
            } else {
                transcript = callAudioTranscription(session.getAudioPath());
                if (StrUtil.isBlank(transcript)) {
                    throw new BusinessException("阿里云语音转文字未返回识别文本");
                }
            }
            session.setTranscriptText(abbreviate(transcript, 20000));
            String summary = callVideoSummaryLlm(transcript);
            session.setSummaryText(abbreviate(summary, 5000));
            session.setSummaryStatus("COMPLETED");
            videoSessionMapper.updateById(session);
        } catch (Exception ex) {
            session.setSummaryStatus("FAILED");
            session.setSummaryText("视频转写或会议概要生成失败。原因：" + abbreviate(ex.getMessage(), 5000));
            videoSessionMapper.updateById(session);
        }
    }

    private String callAudioTranscription(String audioPath) {
        SpeechTranscriptionConfig config = resolveSpeechTranscriptionConfig();
        String accessKeyId = config.accessKeyId();
        String accessKeySecret = config.accessKeySecret();
        String appKey = config.appKey();
        String endpoint = config.endpoint();
        if (StrUtil.isBlank(accessKeyId)) {
            throw new BusinessException("阿里云语音转文字未配置AccessKey ID");
        }
        if (StrUtil.isBlank(accessKeySecret) || StrUtil.isBlank(appKey)) {
            throw new BusinessException("Aliyun speech transcription requires an AccessKey Secret and AppKey");
        }
        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        try {
            accessToken.apply();
        } catch (Exception ex) {
            throw new BusinessException("阿里云语音转文字获取AccessToken失败: " + abbreviate(ex.getMessage()));
        }
        StringBuilder text = new StringBuilder();
        NlsClient client = new NlsClient(endpoint, accessToken.getToken());
        try {
            SpeechRecognizer recognizer = new SpeechRecognizer(client, new SpeechRecognizerListener() {
                @Override
                public void onStarted(com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse response) {
                }

                @Override
                public void onRecognitionResultChanged(com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse response) {
                }

                @Override
                public void onRecognitionCompleted(com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse response) {
                    if (StrUtil.isNotBlank(response.getRecognizedText())) {
                        text.append(response.getRecognizedText()).append('\n');
                    }
                }

                @Override
                public void onFail(com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse response) {
                    throw new BusinessException("阿里云语音识别失败: " + response.getStatusText());
                }
            });
            recognizer.setAppKey(appKey);
            recognizer.setFormat(InputFormatEnum.PCM);
            recognizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            recognizer.setEnableIntermediateResult(false);
            recognizer.start();
            byte[] buffer = new byte[3200];
            try (java.io.InputStream inputStream = Files.newInputStream(Path.of(audioPath))) {
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    recognizer.send(java.util.Arrays.copyOf(buffer, len));
                    Thread.sleep(100);
                }
            }
            recognizer.stop();
        } catch (Exception ex) {
            throw new BusinessException("阿里云语音转文字失败: " + abbreviate(ex.getMessage()));
        } finally {
            client.shutdown();
        }
        return text.toString();
    }

    private String callVideoSummaryLlm(String transcript) {
        InterviewLlmConfig config = requireActiveLlmConfig("VIDEO_SUMMARY");
        String systemPrompt = StrUtil.blankToDefault(config.getScoringRulePrompt(), config.getPromptTemplate());
        if (StrUtil.isBlank(systemPrompt)) {
            systemPrompt = "你是HR视频面试会议纪要助手，请根据面试转写内容输出中文会议概要，包含候选人表现、关键回答、风险点和后续建议。";
        }
        return callOpenAiChat(config, systemPrompt, "视频面试转写：\n" + abbreviate(transcript, 20000));
    }

    private SpeechTranscriptionConfig resolveSpeechTranscriptionConfig() {
        Map<String, String> settings = systemConfigService.loadConfig(STT_CONFIG_KEYS);
        String accessKeyId = StrUtil.blankToDefault(settings.get("ALIYUN_STT_ACCESS_KEY_ID"), "").trim();
        String accessKeySecret = StrUtil.blankToDefault(settings.get("ALIYUN_STT_ACCESS_KEY_SECRET"), "").trim();
        String appKey = StrUtil.blankToDefault(settings.get("ALIYUN_STT_APP_KEY"), "").trim();
        String endpoint = StrUtil.blankToDefault(settings.get("ALIYUN_STT_ENDPOINT"),
                "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1").trim();
        return new SpeechTranscriptionConfig(accessKeyId, accessKeySecret, appKey, endpoint);
    }

    private record SpeechTranscriptionConfig(String accessKeyId, String accessKeySecret, String appKey, String endpoint) {
    }

    private record LlmEvaluation(int score, String comment, String nextQuestion) {
    }

    private InterviewLlmConfig requireActiveLlmConfig(String role) {
        InterviewLlmConfig config = llmConfigMapper.selectOne(new LambdaQueryWrapper<InterviewLlmConfig>()
                .eq(InterviewLlmConfig::getModelRole, role)
                .eq(InterviewLlmConfig::getStatus, 1)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException("未配置启用的LLM模型: " + role);
        }
        if (StrUtil.isBlank(config.getApiKey())) {
            throw new BusinessException("LLM模型未配置API Key: " + role);
        }
        return config;
    }

    private String callOpenAiChat(InterviewLlmConfig config, String systemPrompt, String userPrompt) {
        cn.hutool.json.JSONObject payload = buildChatPayload(config, systemPrompt, userPrompt, false);
        debugLlm("REQUEST", config, systemPrompt, userPrompt, null, null);
        cn.hutool.http.HttpResponse httpResponse;
        try {
            httpResponse = cn.hutool.http.HttpRequest.post(resolveChatCompletionsUrl(config.getBaseUrl()))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(payload.toString())
                    .timeout(15000)
                    .execute();
        } catch (Exception ex) {
            throw new BusinessException("LLM接口调用失败: " + abbreviate(ex.getMessage()));
        }
        String responseText = httpResponse.body();
        debugLlm("RESPONSE", config, systemPrompt, userPrompt, httpResponse.getStatus(), responseText);
        if (!httpResponse.isOk()) {
            throw new BusinessException("LLM接口调用失败，HTTP " + httpResponse.getStatus() + ": " + abbreviate(responseText));
        }
        cn.hutool.json.JSONObject response;
        try {
            response = cn.hutool.json.JSONUtil.parseObj(responseText);
        } catch (Exception ex) {
            throw new BusinessException("LLM接口返回不是有效JSON: " + abbreviate(responseText));
        }
        cn.hutool.json.JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            String error = response.getByPath("error.message", String.class);
            throw new BusinessException("LLM接口返回缺少choices，请检查接口地址、模型名和API Key: " + abbreviate(StrUtil.blankToDefault(error, responseText)));
        }
        cn.hutool.json.JSONObject firstChoice = choices.getJSONObject(0);
        if (firstChoice == null) {
            throw new BusinessException("LLM接口返回choices格式异常: " + abbreviate(responseText));
        }
        cn.hutool.json.JSONObject message = firstChoice.getJSONObject("message");
        if (message == null || StrUtil.isBlank(message.getStr("content"))) {
            throw new BusinessException("LLM接口返回缺少message.content: " + abbreviate(responseText));
        }
        return message.getStr("content", "");
    }

    private String callOpenAiChatStream(InterviewLlmConfig config, String systemPrompt, String userPrompt, Consumer<String> chunkConsumer) {
        cn.hutool.json.JSONObject payload = buildChatPayload(config, systemPrompt, userPrompt, true);
        debugLlm("STREAM_REQUEST", config, systemPrompt, userPrompt, null, null);
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(resolveChatCompletionsUrl(config.getBaseUrl())))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(120))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpResponse<java.io.InputStream> response;
        try {
            response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception ex) {
            throw new BusinessException("LLM流式接口调用失败: " + abbreviate(ex.getMessage()));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("LLM流式接口调用失败，HTTP " + response.statusCode());
        }
        StringBuilder fullText = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                String delta = extractStreamDelta(data);
                if (StrUtil.isNotEmpty(delta)) {
                    fullText.append(delta);
                    chunkConsumer.accept(delta);
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("读取LLM流式输出失败: " + abbreviate(ex.getMessage()));
        }
        debugLlm("STREAM_RESPONSE", config, systemPrompt, userPrompt, response.statusCode(), fullText.toString());
        if (StrUtil.isBlank(fullText.toString())) {
            throw new BusinessException("LLM流式接口未返回有效内容");
        }
        return fullText.toString();
    }

    private cn.hutool.json.JSONObject buildChatPayload(InterviewLlmConfig config, String systemPrompt, String userPrompt, boolean stream) {
        cn.hutool.json.JSONObject payload = new cn.hutool.json.JSONObject();
        payload.set("model", config.getModelName());
        payload.set("messages", cn.hutool.json.JSONUtil.parseArray(List.of(
                new cn.hutool.json.JSONObject().set("role", "system").set("content", systemPrompt),
                new cn.hutool.json.JSONObject().set("role", "user").set("content", userPrompt)
        )));
        if (stream) {
            payload.set("stream", true);
        }
        return payload;
    }

    private String extractStreamDelta(String data) {
        try {
            cn.hutool.json.JSONObject chunk = cn.hutool.json.JSONUtil.parseObj(data);
            cn.hutool.json.JSONArray choices = chunk.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            cn.hutool.json.JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            return delta == null ? "" : StrUtil.blankToDefault(delta.getStr("content"), "");
        } catch (Exception ex) {
            return "";
        }
    }

    private void debugLlm(String phase, InterviewLlmConfig config, String systemPrompt, String userPrompt, Integer httpStatus, String output) {
        if (!llmDebug) {
            return;
        }
        String text = "\n================ LLM " + phase + " " + LocalDateTime.now() + " ================\n"
                + "配置ID: " + config.getId() + "\n"
                + "配置名称: " + config.getConfigName() + "\n"
                + "模型角色: " + config.getModelRole() + "\n"
                + "接口地址: " + resolveChatCompletionsUrl(config.getBaseUrl()) + "\n"
                + "模型名称: " + config.getModelName() + "\n"
                + (httpStatus == null ? "" : "HTTP状态: " + httpStatus + "\n")
                + "--- SYSTEM 输入 ---\n" + StrUtil.blankToDefault(systemPrompt, "") + "\n"
                + "--- USER 输入 ---\n" + StrUtil.blankToDefault(userPrompt, "") + "\n"
                + "--- LLM 输出 ---\n" + StrUtil.blankToDefault(output, "") + "\n";
        try {
            Files.writeString(Paths.get(System.getProperty("user.dir"), "LLM.txt"), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private String abbreviate(String text) {
        if (StrUtil.isBlank(text)) {
            return "空响应";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    private String abbreviate(String text, int maxLength) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String url = StrUtil.trim(baseUrl);
        if (StrUtil.endWithIgnoreCase(url, "/chat/completions")) {
            return url;
        }
        if (StrUtil.endWithIgnoreCase(url, "/v1")) {
            return url + "/chat/completions";
        }
        return StrUtil.removeSuffix(url, "/") + "/v1/chat/completions";
    }

    private String normalizeAiOutputMode(String mode) {
        return StrUtil.equalsIgnoreCase(mode, "STREAM") ? "STREAM" : "NORMAL";
    }

    private int nextSequence(Long processId, Long processStageId) {
        return aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessId, processId)
                .eq(processStageId != null, InterviewAiRecord::getProcessStageId, processStageId)
                .isNull(processStageId == null, InterviewAiRecord::getProcessStageId)).size() + 1;
    }

    private InterviewVO toKnowledgeBaseVO(InterviewKnowledgeBase entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setKnowledgeBaseName(entity.getKnowledgeBaseName());
        vo.setTechCategory(entity.getTechCategory());
        vo.setJobCategory(entity.getJobCategory());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toKnowledgeItemVO(InterviewKnowledgeItem entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        vo.setKnowledgePoint(entity.getKnowledgePoint());
        vo.setKnowledgeContent(entity.getKnowledgeContent());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toJobKnowledgeWeightVO(InterviewJobKnowledgeWeight entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setJobId(entity.getJobId());
        vo.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        vo.setWeight(entity.getWeight());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toLlmConfigVO(InterviewLlmConfig entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setConfigName(entity.getConfigName());
        vo.setModelRole(entity.getModelRole());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        vo.setModelName(entity.getModelName());
        vo.setPromptTemplate(entity.getPromptTemplate());
        vo.setScoringRulePrompt(entity.getScoringRulePrompt());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toProcessTemplateVO(InterviewProcessTemplate entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getId());
        vo.setTemplateName(entity.getTemplateName());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setStages(listTemplateStages(entity.getId()).stream().map(this::toTemplateStageVO).toList());
        return vo;
    }

    private InterviewVO toTemplateStageVO(InterviewProcessTemplateStage entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setStageName(entity.getStageName());
        vo.setStageType(entity.getStageType());
        vo.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        vo.setSequenceNo(entity.getSequenceNo());
        vo.setKnowledgeBaseName("AI".equals(entity.getStageType()) ? loadKnowledgeBaseName(entity.getKnowledgeBaseId()) : null);
        return vo;
    }

    private InterviewVO toProcessStageVO(InterviewProcessStage entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setProcessId(entity.getProcessId());
        vo.setProcessStageId(entity.getId());
        vo.setStageName(entity.getStageName());
        vo.setStageType(entity.getStageType());
        vo.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        vo.setKnowledgeBaseName("AI".equals(entity.getStageType()) ? loadKnowledgeBaseName(entity.getKnowledgeBaseId()) : null);
        vo.setSequenceNo(entity.getSequenceNo());
        vo.setStageStatus(entity.getStageStatus());
        vo.setApprovedHrUserId(entity.getApprovedHrUserId());
        vo.setApprovedHrName(entity.getApprovedHrName());
        vo.setAiRecordingPath(entity.getAiRecordingPath());
        vo.setAiRecordingFileName(entity.getAiRecordingFileName());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toProcessVO(InterviewProcess entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setRecruitmentCandidateId(entity.getRecruitmentCandidateId());
        vo.setIntervieweeUserId(entity.getIntervieweeUserId());
        vo.setJobId(entity.getJobId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setTemplateName(entity.getTemplateName());
        RecruitmentCandidate candidate = recruitmentCandidateMapper.selectById(entity.getRecruitmentCandidateId());
        if (candidate != null) {
            vo.setCandidateName(candidate.getFullName());
        }
        RecruitmentJob job = recruitmentJobMapper.selectById(entity.getJobId());
        if (job != null) {
            vo.setQuestionTitle(job.getJobTitle());
            vo.setJobDepartmentId(job.getDepartmentId());
            vo.setJobDepartmentName(job.getDepartmentName());
        }
        vo.setCurrentStage(entity.getCurrentStage());
        vo.setStageStatus(entity.getStageStatus());
        vo.setOverallStatus(entity.getOverallStatus());
        vo.setAiThresholdScore(entity.getAiThresholdScore());
        vo.setAiFollowUpThreshold(entity.getAiFollowUpThreshold());
        vo.setAiAverageScore(entity.getAiAverageScore());
        vo.setAiMinQuestionRounds(entity.getAiMinQuestionRounds());
        vo.setAiMaxQuestionRounds(entity.getAiMaxQuestionRounds());
        vo.setAntiCheatSwitchLimit(entity.getAntiCheatSwitchLimit());
        vo.setAntiCheatSwitchCount(entity.getAntiCheatSwitchCount());
        vo.setAiOutputMode(entity.getAiOutputMode());
        vo.setVideoApproved(entity.getVideoApproved());
        vo.setOnsiteApproved(entity.getOnsiteApproved());
        vo.setApprovedHrUserId(entity.getApprovedHrUserId());
        vo.setApprovedHrName(entity.getApprovedHrName());
        vo.setProcessStatusView(entity.getProcessStatusView());
        vo.setRemark(entity.getRemark());
        vo.setAiRecordingPath(entity.getAiRecordingPath());
        vo.setAiRecordingFileName(entity.getAiRecordingFileName());
        if (isTemplateProcess(entity)) {
            List<InterviewProcessStage> stages = listProcessStages(entity.getId());
            vo.setStages(stages.stream().map(this::toProcessStageVO).toList());
            InterviewProcessStage active = stages.stream()
                    .filter(stage -> List.of("IN_PROGRESS", "READY", "UPLOADING", "WAITING_APPROVAL").contains(stage.getStageStatus()))
                    .findFirst().orElse(null);
            if (active != null) {
                vo.setProcessStageId(active.getId());
                vo.setStageName(active.getStageName());
                vo.setStageType(active.getStageType());
                vo.setKnowledgeBaseId(active.getKnowledgeBaseId());
                vo.setKnowledgeBaseName("AI".equals(active.getStageType()) ? loadKnowledgeBaseName(active.getKnowledgeBaseId()) : null);
                if ("VIDEO".equals(active.getStageType())) {
                    fillVideoSessionSummary(vo, entity.getId(), active.getId());
                }
                if ("AI".equals(active.getStageType())) {
                    vo.setAiRecordingPath(active.getAiRecordingPath());
                    vo.setAiRecordingFileName(active.getAiRecordingFileName());
                }
            }
        } else {
            fillVideoSessionSummary(vo, entity.getId(), null);
        }
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toIntervieweeProcessVO(InterviewProcess entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setProcessId(entity.getId());
        vo.setCurrentStage(entity.getCurrentStage());
        vo.setStageStatus(entity.getStageStatus());
        vo.setOverallStatus(entity.getOverallStatus());
        vo.setAiAverageScore(entity.getAiAverageScore());
        vo.setAiMaxQuestionRounds(entity.getAiMaxQuestionRounds());
        vo.setAntiCheatSwitchLimit(entity.getAntiCheatSwitchLimit());
        vo.setAntiCheatSwitchCount(entity.getAntiCheatSwitchCount());
        vo.setAiOutputMode(entity.getAiOutputMode());
        vo.setProcessStatusView(entity.getProcessStatusView());
        if (isTemplateProcess(entity)) {
            InterviewProcessStage active = listProcessStages(entity.getId()).stream()
                    .filter(stage -> List.of("IN_PROGRESS", "READY", "UPLOADING", "WAITING_APPROVAL").contains(stage.getStageStatus()))
                    .findFirst().orElse(null);
            if (active != null) {
                vo.setProcessStageId(active.getId());
                vo.setStageName(active.getStageName());
                vo.setStageType(active.getStageType());
            }
        }
        return vo;
    }

    private InterviewVO toAiRecordVO(InterviewAiRecord entity, InterviewProcess process) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setProcessId(entity.getProcessId());
        vo.setProcessStageId(entity.getProcessStageId());
        if (entity.getProcessStageId() != null) {
            InterviewProcessStage stage = processStageMapper.selectById(entity.getProcessStageId());
            if (stage != null) {
                vo.setStageName(stage.getStageName());
                vo.setStageType(stage.getStageType());
            }
        }
        vo.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        vo.setKnowledgePoint(entity.getKnowledgePoint());
        vo.setQuestionContent(entity.getQuestionContent());
        vo.setAnswerContent(entity.getAnswerContent());
        vo.setInterviewerScore(entity.getInterviewerScore());
        vo.setScorerScore(entity.getScorerScore());
        vo.setAverageScore(entity.getAverageScore());
        vo.setInterviewerComment(entity.getInterviewerComment());
        vo.setSequenceNo(entity.getSequenceNo());
        vo.setCreatedAt(entity.getCreatedAt());
        if (process != null) {
            vo.setCurrentStage(process.getCurrentStage());
            vo.setStageStatus(process.getStageStatus());
            vo.setProcessStatusView(process.getProcessStatusView());
        }
        return vo;
    }

    private InterviewVO toVideoSessionVO(InterviewVideoSession entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setProcessId(entity.getProcessId());
        vo.setProcessStageId(entity.getProcessStageId());
        vo.setVideoSerialNo(entity.getVideoSerialNo());
        vo.setVideoJoinLink(entity.getVideoJoinLink());
        vo.setApproverUserId(entity.getApproverUserId());
        vo.setApproverName(entity.getApproverName());
        vo.setIntervieweeJoinTime(entity.getIntervieweeJoinTime());
        vo.setHrJoinTime(entity.getHrJoinTime());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setRecordingPath(StrUtil.blankToDefault(entity.getMergedRecordingPath(), entity.getRecordingPath()));
        vo.setRecordingFileName(StrUtil.blankToDefault(entity.getMergedRecordingFileName(), entity.getRecordingFileName()));
        vo.setSessionStatus(entity.getSessionStatus());
        vo.setRecordingEndRequestedAt(entity.getRecordingEndRequestedAt());
        vo.setAudioPath(entity.getAudioPath());
        vo.setAudioFileName(entity.getAudioFileName());
        vo.setTranscriptText(entity.getTranscriptText());
        vo.setSummaryText(entity.getSummaryText());
        vo.setSummaryStatus(entity.getSummaryStatus());
        return vo;
    }

    private InterviewVO toIntervieweeVideoSessionVO(InterviewVideoSession entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setProcessId(entity.getProcessId());
        vo.setProcessStageId(entity.getProcessStageId());
        vo.setSessionStatus(entity.getSessionStatus());
        vo.setRecordingEndRequestedAt(entity.getRecordingEndRequestedAt());
        return vo;
    }

    private void fillVideoSessionSummary(InterviewVO vo, Long processId, Long processStageId) {
        InterviewVideoSession session = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .eq(processStageId != null, InterviewVideoSession::getProcessStageId, processStageId)
                .isNull(processStageId == null, InterviewVideoSession::getProcessStageId)
                .last("LIMIT 1"));
        if (session == null) {
            return;
        }
        vo.setVideoSerialNo(session.getVideoSerialNo());
        vo.setVideoJoinLink(session.getVideoJoinLink());
        vo.setIntervieweeJoinTime(session.getIntervieweeJoinTime());
        vo.setHrJoinTime(session.getHrJoinTime());
        vo.setRecordingEndRequestedAt(session.getRecordingEndRequestedAt());
        vo.setRecordingPath(StrUtil.blankToDefault(session.getMergedRecordingPath(), session.getRecordingPath()));
        vo.setRecordingFileName(StrUtil.blankToDefault(session.getMergedRecordingFileName(), session.getRecordingFileName()));
        vo.setSessionStatus(session.getSessionStatus());
        vo.setAudioPath(session.getAudioPath());
        vo.setAudioFileName(session.getAudioFileName());
        vo.setTranscriptText(session.getTranscriptText());
        vo.setSummaryText(session.getSummaryText());
        vo.setSummaryStatus(session.getSummaryStatus());
    }

    private VideoSignalVO toVideoSignalVO(InterviewVideoSession entity) {
        VideoSignalVO vo = new VideoSignalVO();
        vo.setSessionId(entity.getId());
        vo.setProcessId(entity.getProcessId());
        vo.setVideoSerialNo(entity.getVideoSerialNo());
        vo.setVideoJoinLink(entity.getVideoJoinLink());
        vo.setOfferSdp(entity.getHrOfferSdp());
        vo.setAnswerSdp(entity.getIntervieweeAnswerSdp());
        vo.setHrIceCandidates(currentIceCandidates(entity.getHrOfferSdp(), entity.getHrIceCandidates()));
        vo.setIntervieweeIceCandidates(currentIceCandidates(entity.getIntervieweeAnswerSdp(), entity.getIntervieweeIceCandidates()));
        vo.setRecordingPath(StrUtil.blankToDefault(entity.getMergedRecordingPath(), entity.getRecordingPath()));
        vo.setRecordingFileName(StrUtil.blankToDefault(entity.getMergedRecordingFileName(), entity.getRecordingFileName()));
        vo.setSessionStatus(entity.getSessionStatus());
        vo.setRecordingEndRequestedAt(entity.getRecordingEndRequestedAt());
        vo.setTranscriptText(entity.getTranscriptText());
        vo.setSummaryText(entity.getSummaryText());
        vo.setSummaryStatus(entity.getSummaryStatus());
        return vo;
    }

    private VideoSignalVO toIntervieweeVideoSignalVO(InterviewVideoSession entity) {
        VideoSignalVO vo = new VideoSignalVO();
        vo.setProcessId(entity.getProcessId());
        vo.setOfferSdp(entity.getHrOfferSdp());
        vo.setHrIceCandidates(currentIceCandidates(entity.getHrOfferSdp(), entity.getHrIceCandidates()));
        vo.setSessionStatus(entity.getSessionStatus());
        vo.setRecordingEndRequestedAt(entity.getRecordingEndRequestedAt());
        return vo;
    }

    private String currentIceCandidates(String sessionDescription, String candidates) {
        if (StrUtil.isBlank(candidates)) {
            return candidates;
        }
        return java.util.Arrays.stream(candidates.split("\\n"))
                .filter(StrUtil::isNotBlank)
                .filter(item -> isCurrentIceCandidate(sessionDescription, item))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private String validateIceCandidate(String candidate) {
        if (StrUtil.isBlank(candidate)) {
            throw new BusinessException("ICE候选不能为空");
        }
        if (candidate.length() > MAX_ICE_CANDIDATE_LENGTH) {
            throw new BusinessException("ICE候选不能超过4096个字符");
        }
        return candidate;
    }

    private String appendIceCandidate(String existing, String candidate) {
        if (StrUtil.isBlank(existing)) {
            return candidate;
        }
        long existingCount = java.util.Arrays.stream(existing.split("\\n"))
                .filter(StrUtil::isNotBlank)
                .count();
        if (existingCount >= MAX_ICE_CANDIDATE_COUNT) {
            throw new BusinessException("ICE候选数量已达到上限");
        }
        if (existing.length() + candidate.length() + 1 > MAX_ICE_CANDIDATES_LENGTH) {
            throw new BusinessException("ICE候选总长度已达到上限");
        }
        return existing + "\n" + candidate;
    }

    private boolean containsSignal(String existing, String value) {
        if (StrUtil.isBlank(existing) || StrUtil.isBlank(value)) {
            return false;
        }
        return java.util.Arrays.stream(existing.split("\\n"))
                .anyMatch(item -> StrUtil.equals(item, value));
    }

    private boolean isReadableFile(String path) {
        return StrUtil.isNotBlank(path) && Files.isRegularFile(Path.of(path)) && Files.isReadable(Path.of(path));
    }

    private boolean isCurrentIceCandidate(String sessionDescription, String iceCandidate) {
        if (StrUtil.isBlank(sessionDescription) || StrUtil.isBlank(iceCandidate)) {
            return false;
        }
        String sdpUfrag = extractSdpIceUfrag(sessionDescription);
        String candidateUfrag = extractCandidateIceUfrag(iceCandidate);
        if (StrUtil.isBlank(sdpUfrag) || StrUtil.isBlank(candidateUfrag)) {
            return true;
        }
        return StrUtil.equals(sdpUfrag, candidateUfrag);
    }

    private String extractSdpIceUfrag(String sessionDescription) {
        String normalized = StrUtil.blankToDefault(sessionDescription, "")
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\r\n", "\n");
        Matcher matcher = SDP_ICE_UFRAG_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractCandidateIceUfrag(String iceCandidate) {
        Matcher jsonMatcher = CANDIDATE_JSON_UFRAG_PATTERN.matcher(iceCandidate);
        if (jsonMatcher.find()) {
            return jsonMatcher.group(1).trim();
        }
        Matcher sdpMatcher = CANDIDATE_SDP_UFRAG_PATTERN.matcher(iceCandidate);
        return sdpMatcher.find() ? sdpMatcher.group(1).trim() : null;
    }

    private boolean isTerminalVideoSessionStatus(String status) {
        return StrUtil.equalsAny(status, "WAITING_APPROVAL", "RECORDED", "PASSED", "REJECTED");
    }

    private boolean canStartSynchronizedRecording(InterviewVideoSession session) {
        return session.getHrJoinTime() != null
                && session.getIntervieweeJoinTime() != null
                && StrUtil.isNotBlank(session.getHrOfferSdp())
                && StrUtil.isNotBlank(session.getIntervieweeAnswerSdp());
    }

    private boolean hasAnyRecording(InterviewVideoSession session) {
        return StrUtil.isNotBlank(session.getRecordingPath())
                || StrUtil.isNotBlank(session.getHrRecordingPath())
                || StrUtil.isNotBlank(session.getIntervieweeRecordingPath())
                || StrUtil.isNotBlank(session.getMergedRecordingPath());
    }

    private Boolean markMissingRecordingForApproval(Long sessionId,
                                                    LocalDateTime cutoff,
                                                    org.springframework.transaction.TransactionStatus transactionStatus) {
        InterviewVideoSession session = videoSessionMapper.selectById(sessionId);
        if (session == null
                || !StrUtil.equals(session.getSessionStatus(), "END_REQUESTED")
                || session.getEndTime() == null
                || session.getEndTime().isAfter(cutoff)
                || videoMergeService.canMerge(session)) {
            return false;
        }
        InterviewProcess process = processMapper.selectById(session.getProcessId());
        if (process == null
                || !StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")
                || !StrUtil.equals(process.getCurrentStage(), "VIDEO")
                || !StrUtil.equals(process.getStageStatus(), "UPLOADING")) {
            return false;
        }

        InterviewProcessStage stage = null;
        String processStatusView = "视频待审批（录像缺失）";
        if (isTemplateProcess(process)) {
            stage = processStageMapper.selectById(session.getProcessStageId());
            if (stage == null
                    || !Objects.equals(stage.getProcessId(), process.getId())
                    || !StrUtil.equals(stage.getStageType(), "VIDEO")
                    || !StrUtil.equals(stage.getStageStatus(), "UPLOADING")) {
                return false;
            }
            processStatusView = StrUtil.blankToDefault(stage.getStageName(), "视频面试") + "待审批（录像缺失）";
        }

        int sessionUpdated = videoSessionMapper.update(null, new LambdaUpdateWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getId, sessionId)
                .eq(InterviewVideoSession::getSessionStatus, "END_REQUESTED")
                .isNotNull(InterviewVideoSession::getEndTime)
                .le(InterviewVideoSession::getEndTime, cutoff)
                .set(InterviewVideoSession::getSessionStatus, "WAITING_APPROVAL")
                .set(InterviewVideoSession::getSummaryStatus, "MISSING_RECORDING")
                .set(InterviewVideoSession::getSummaryText, "录像结束后 10 分钟内未完整收到双方录像，已标记缺失；审批流程不受阻，迟到录像上传后仍会自动合并。"));
        if (sessionUpdated != 1) {
            return false;
        }

        if (stage != null) {
            int stageUpdated = processStageMapper.update(null, new LambdaUpdateWrapper<InterviewProcessStage>()
                    .eq(InterviewProcessStage::getId, stage.getId())
                    .eq(InterviewProcessStage::getProcessId, process.getId())
                    .eq(InterviewProcessStage::getStageType, "VIDEO")
                    .eq(InterviewProcessStage::getStageStatus, "UPLOADING")
                    .set(InterviewProcessStage::getStageStatus, "WAITING_APPROVAL"));
            if (stageUpdated != 1) {
                transactionStatus.setRollbackOnly();
                return false;
            }
        }

        int processUpdated = processMapper.update(null, new LambdaUpdateWrapper<InterviewProcess>()
                .eq(InterviewProcess::getId, process.getId())
                .eq(InterviewProcess::getOverallStatus, "IN_PROGRESS")
                .eq(InterviewProcess::getCurrentStage, "VIDEO")
                .eq(InterviewProcess::getStageStatus, "UPLOADING")
                .set(InterviewProcess::getStageStatus, "WAITING_APPROVAL")
                .set(InterviewProcess::getProcessStatusView, processStatusView));
        if (processUpdated != 1) {
            transactionStatus.setRollbackOnly();
            return false;
        }

        process.setStageStatus("WAITING_APPROVAL");
        process.setProcessStatusView(processStatusView);
        updateCandidateStage(process);
        auditLogService.log(null, "SYSTEM", "SYSTEM", "INTERVIEW", "VIDEO_RECORDING_TIMEOUT_WARNING",
                "VIDEO_SESSION", String.valueOf(sessionId), processStatusView);
        return true;
    }

    private void markVideoWaitingApproval(Long processId) {
        InterviewProcess process = requireProcess(processId);
        if (isTemplateProcess(process)) {
            InterviewProcessStage stage = requireActiveProcessStage(process);
            if ("VIDEO".equals(stage.getStageType()) && StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
                setTemplateStageStatus(process, stage, "WAITING_APPROVAL");
                processMapper.updateById(process);
                updateCandidateStage(process);
            }
            return;
        }
        if (StrUtil.equals(process.getCurrentStage(), "VIDEO") && StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            process.setStageStatus("WAITING_APPROVAL");
            process.setProcessStatusView("视频待审批");
            processMapper.updateById(process);
            updateCandidateStage(process);
        }
    }

    private String displayName(String name, String fallback) {
        return StrUtil.blankToDefault(name, fallback);
    }

    private String maskApiKey(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return "未配置";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

}
