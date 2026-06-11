package com.autohr.modules.interview.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.enums.EmploymentStatus;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.AntiCheatEventRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
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
import com.autohr.modules.interview.entity.InterviewVideoSession;
import com.autohr.modules.interview.mapper.InterviewAiRecordMapper;
import com.autohr.modules.interview.mapper.InterviewJobKnowledgeWeightMapper;
import com.autohr.modules.interview.mapper.InterviewKnowledgeBaseMapper;
import com.autohr.modules.interview.mapper.InterviewKnowledgeItemMapper;
import com.autohr.modules.interview.mapper.InterviewLlmConfigMapper;
import com.autohr.modules.interview.mapper.InterviewProcessMapper;
import com.autohr.modules.interview.mapper.InterviewVideoSessionMapper;
import com.autohr.modules.interview.service.InterviewService;
import com.autohr.modules.interview.service.VideoMergeService;
import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final Pattern SDP_ICE_UFRAG_PATTERN = Pattern.compile("(?m)^a=ice-ufrag:([^\\r\\n]+)");
    private static final Pattern CANDIDATE_JSON_UFRAG_PATTERN = Pattern.compile("\\\"usernameFragment\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern CANDIDATE_SDP_UFRAG_PATTERN = Pattern.compile("(?:^|\\s)ufrag\\s+([^\\s]+)");

    private final InterviewKnowledgeBaseMapper knowledgeBaseMapper;
    private final InterviewKnowledgeItemMapper knowledgeItemMapper;
    private final InterviewJobKnowledgeWeightMapper jobKnowledgeWeightMapper;
    private final InterviewLlmConfigMapper llmConfigMapper;
    private final InterviewProcessMapper processMapper;
    private final InterviewAiRecordMapper aiRecordMapper;
    private final InterviewVideoSessionMapper videoSessionMapper;
    private final RecruitmentCandidateMapper recruitmentCandidateMapper;
    private final RecruitmentJobMapper recruitmentJobMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final VideoMergeService videoMergeService;

    @Value("${interview.llm.debug:false}")
    private boolean llmDebug;

    private static final long MAX_RECORDING_SIZE = 100 * 1024 * 1024;
    private static final Set<String> ALLOWED_RECORDING_CONTENT_TYPES = Set.of("video/webm", "application/octet-stream");

    @Override
    @Transactional
    public InterviewVO saveKnowledgeBase(KnowledgeBaseSaveRequest request) {
        InterviewKnowledgeBase entity = request.getId() == null ? new InterviewKnowledgeBase() : requireKnowledgeBase(request.getId());
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            entity.setId(nextId(knowledgeBaseMapper.selectList(null).stream().map(InterviewKnowledgeBase::getId).toList()));
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
            entity.setId(nextId(knowledgeItemMapper.selectList(null).stream().map(InterviewKnowledgeItem::getId).toList()));
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
            entity.setId(nextId(knowledgeItemMapper.selectList(null).stream().map(InterviewKnowledgeItem::getId).toList()));
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
            entity.setId(nextId(jobKnowledgeWeightMapper.selectList(null).stream().map(InterviewJobKnowledgeWeight::getId).toList()));
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
            entity.setId(nextId(llmConfigMapper.selectList(null).stream().map(InterviewLlmConfig::getId).toList()));
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
        process.setId(nextId(processMapper.selectList(null).stream().map(InterviewProcess::getId).toList()));
        process.setRecruitmentCandidateId(candidate.getId());
        process.setIntervieweeUserId(request.getIntervieweeUserId());
        process.setJobId(request.getJobId());
        process.setCurrentStage("AI");
        process.setStageStatus("IN_PROGRESS");
        process.setOverallStatus("IN_PROGRESS");
        process.setAiThresholdScore(Objects.requireNonNullElse(request.getAiThresholdScore(), 70));
        int minQuestionRounds = Math.max(Objects.requireNonNullElse(request.getAiMinQuestionRounds(), 1), 1);
        int maxQuestionRounds = Math.max(Objects.requireNonNullElse(request.getAiMaxQuestionRounds(), 10), minQuestionRounds);
        process.setAiMinQuestionRounds(minQuestionRounds);
        process.setAiMaxQuestionRounds(maxQuestionRounds);
        process.setAntiCheatSwitchLimit(Math.max(Objects.requireNonNullElse(request.getAntiCheatSwitchLimit(), 5), 1));
        process.setAntiCheatSwitchCount(0);
        process.setVideoApproved(0);
        process.setOnsiteApproved(0);
        process.setProcessStatusView("AI面");
        processMapper.insert(process);
        candidate.setInterviewProcessId(process.getId());
        candidate.setInterviewStageStatus("AI面");
        candidate.setApplicationStatus("INTERVIEWING");
        recruitmentCandidateMapper.updateById(candidate);
        runAfterCommit(() -> CompletableFuture.runAsync(() -> generateInitialQuestionSafely(process.getId())));
        return toProcessVO(process);
    }

    @Override
    public List<InterviewVO> listProcesses(String overallStatus, String stageStatus, String keyword) {
        return processMapper.selectList(new LambdaQueryWrapper<InterviewProcess>()
                .eq(StrUtil.isNotBlank(overallStatus), InterviewProcess::getOverallStatus, overallStatus)
                .eq(StrUtil.isNotBlank(stageStatus), InterviewProcess::getCurrentStage, stageStatus)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewProcess::getProcessStatusView, keyword)
                        .or().like(InterviewProcess::getApprovedHrName, keyword))
                .orderByDesc(InterviewProcess::getId)).stream().map(this::toProcessVO).toList();
    }

    @Override
    public InterviewVO getProcess(Long processId) {
        return toProcessVO(requireProcess(processId));
    }

    @Override
    public InterviewVO getIntervieweeProcess(Long processId, Long intervieweeUserId) {
        return toProcessVO(requireIntervieweeProcess(processId, intervieweeUserId));
    }

    @Override
    public synchronized InterviewVO getNextAiQuestion(Long processId) {
        InterviewProcess process = requireProcess(processId);
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
        InterviewProcess process = requireProcess(request.getProcessId());
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "AI")) {
            throw new BusinessException("当前流程不在AI面试阶段");
        }
        InterviewAiRecord record = aiRecordMapper.selectOne(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(InterviewAiRecord::getProcessId, process.getId())
                .isNull(InterviewAiRecord::getAnswerContent)
                .orderByAsc(InterviewAiRecord::getSequenceNo)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException("当前没有待回答的问题");
        }
        record.setAnswerContent(request.getAnswerContent());
        String materials = loadKnowledgeMaterials(record.getKnowledgeBaseId());
        String jobRequirements = loadJobRequirements(process);
        LlmEvaluation interviewerEvaluation = callLlmEvaluation(record.getQuestionContent(), request.getAnswerContent(), record.getKnowledgePoint(), materials, jobRequirements, "INTERVIEWER", true);
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
        int minQuestionRounds = Math.max(Objects.requireNonNullElse(process.getAiMinQuestionRounds(), 1), 1);
        if (answeredRounds >= minQuestionRounds && currentAverage >= process.getAiThresholdScore()) {
            process.setStageStatus("WAITING_APPROVAL");
            process.setProcessStatusView("AI待审批");
        } else if (answeredRounds >= Math.max(Objects.requireNonNullElse(process.getAiMaxQuestionRounds(), 10), 1)) {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
            process.setProcessStatusView("AI未达标自动结束");
            auditLogService.log(process.getIntervieweeUserId(), "面试者", "INTERVIEWEE", "INTERVIEW", "AI_MAX_ROUNDS_REJECT", "INTERVIEW_PROCESS", String.valueOf(process.getId()), "AI均分" + currentAverage + "未达到阈值" + process.getAiThresholdScore() + "，已答" + answeredRounds + "轮达到最大轮数" + process.getAiMaxQuestionRounds());
        } else {
            generateNextQuestion(process);
        }
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        return toAiRecordVO(record, process);
    }

    @Override
    @Transactional
    public InterviewVO submitIntervieweeAiAnswer(AiAnswerRequest request, Long intervieweeUserId) {
        requireIntervieweeProcess(request.getProcessId(), intervieweeUserId);
        return submitAiAnswer(request);
    }

    @Override
    public List<InterviewVO> listAiRecords(Long processId) {
        return aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(processId != null, InterviewAiRecord::getProcessId, processId)
                .orderByAsc(InterviewAiRecord::getSequenceNo)
                .orderByAsc(InterviewAiRecord::getId)).stream().map(item -> toAiRecordVO(item, null)).toList();
    }

    @Override
    public List<InterviewVO> listIntervieweeAiRecords(Long processId, Long intervieweeUserId) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        return listAiRecords(processId);
    }

    @Override
    @Transactional
    public InterviewVO createVideoSession(Long processId, Long approverUserId, String approverName) {
        InterviewProcess process = requireProcess(processId);
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
            return toVideoSessionVO(session);
        }
        session.setIntervieweeJoinTime(LocalDateTime.now());
        if (session.getStartTime() == null) {
            session.setStartTime(LocalDateTime.now());
        }
        session.setSessionStatus("INTERVIEWEE_JOINED");
        videoSessionMapper.updateById(session);
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "INTERVIEWEE_JOIN_VIDEO", "VIDEO_SESSION", String.valueOf(session.getId()), String.valueOf(processId));
        return toVideoSessionVO(session);
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
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (isTerminalVideoSessionStatus(session.getSessionStatus())) {
            return toVideoSessionVO(session);
        }
        if (!isTerminalVideoSessionStatus(session.getSessionStatus()) && !StrUtil.equals(session.getSessionStatus(), "END_REQUESTED")) {
            session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
            session.setSessionStatus("END_REQUESTED");
            videoSessionMapper.updateById(session);
        }
        InterviewProcess process = requireProcess(processId);
        if (StrUtil.equals(process.getCurrentStage(), "VIDEO") && StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            if (videoMergeService.canMerge(session)) {
                try {
                    videoMergeService.mergeRecordings(session);
                } catch (BusinessException ignored) {
                    session.setSessionStatus("RECORDED");
                    videoSessionMapper.updateById(session);
                }
                session.setSessionStatus("RECORDED");
                videoSessionMapper.updateById(session);
                process.setStageStatus("WAITING_APPROVAL");
                process.setProcessStatusView("视频待审批");
            } else {
                process.setStageStatus("UPLOADING");
                process.setProcessStatusView("视频录制上传中");
            }
            processMapper.updateById(process);
            updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        }
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO requestIntervieweeVideoEnd(Long processId, Long intervieweeUserId) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        return completeVideoSession(processId, null);
    }

    @Override
    @Transactional
    public InterviewVO approveAiToVideo(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "AI")) {
            throw new BusinessException("当前流程不在AI审批阶段");
        }
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
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "APPROVE_AI", "INTERVIEW_PROCESS", String.valueOf(processId), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO approveVideoToOnsite(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        ensureInProgress(process);
        if (!StrUtil.equals(process.getCurrentStage(), "VIDEO")) {
            throw new BusinessException("当前流程不在视频审批阶段");
        }
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (!(StrUtil.equals(session.getSessionStatus(), "WAITING_APPROVAL") || StrUtil.equals(session.getSessionStatus(), "RECORDED"))) {
            throw new BusinessException("视频面试尚未结束，不能审批");
        }
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
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
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
        process.setOnsiteApproved(request.getApproved());
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        if (request.getApproved() == 1) {
            process.setOverallStatus("PASSED");
            process.setStageStatus("PASSED");
            process.setProcessStatusView("已通过");
            syncToPendingOnboarding(process);
        } else {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
            process.setProcessStatusView("已拒绝");
        }
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        auditLogService.log(request.getApproverUserId(), displayName(request.getApproverName(), "HR"), "HR_ADMIN", "INTERVIEW", "APPROVE_ONSITE", "INTERVIEW_PROCESS", String.valueOf(processId), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO terminateProcess(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        ensureInProgress(process);
        process.setOverallStatus("TERMINATED");
        process.setStageStatus("TERMINATED");
        process.setProcessStatusView("已终止");
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), "已终止");
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
            return toVideoSignalVO(session);
        }
        session.setIntervieweeAnswerSdp(request.getAnswerSdp());
        if (session.getIntervieweeJoinTime() == null) {
            session.setIntervieweeJoinTime(LocalDateTime.now());
        }
        session.setIntervieweeIceCandidates(null);
        session.setSessionStatus(canStartSynchronizedRecording(session) ? "RECORDING" : "ANSWER_SUBMITTED");
        videoSessionMapper.updateById(session);
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "SUBMIT_VIDEO_ANSWER", "VIDEO_SESSION", String.valueOf(session.getId()), session.getVideoSerialNo());
        return toVideoSignalVO(session);
    }

    @Override
    @Transactional
    public VideoSignalVO addHrIceCandidate(Long processId, VideoSignalRequest request) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (!isCurrentIceCandidate(session.getHrOfferSdp(), request.getIceCandidate())) {
            return toVideoSignalVO(session);
        }
        if (containsSignal(session.getHrIceCandidates(), request.getIceCandidate())) {
            return toVideoSignalVO(session);
        }
        session.setHrIceCandidates(appendSignal(session.getHrIceCandidates(), request.getIceCandidate()));
        videoSessionMapper.updateById(session);
        return toVideoSignalVO(session);
    }

    @Override
    @Transactional
    public VideoSignalVO addIntervieweeIceCandidate(Long processId, VideoSignalRequest request, Long intervieweeUserId, String intervieweeName) {
        requireIntervieweeProcess(processId, intervieweeUserId);
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (!isCurrentIceCandidate(session.getIntervieweeAnswerSdp(), request.getIceCandidate())) {
            return toVideoSignalVO(session);
        }
        if (containsSignal(session.getIntervieweeIceCandidates(), request.getIceCandidate())) {
            return toVideoSignalVO(session);
        }
        session.setIntervieweeIceCandidates(appendSignal(session.getIntervieweeIceCandidates(), request.getIceCandidate()));
        videoSessionMapper.updateById(session);
        return toVideoSignalVO(session);
    }

    @Override
    public VideoSignalVO getVideoSignalState(Long processId) {
        return toVideoSignalVO(requireVideoSessionByProcess(processId));
    }

    @Override
    public InterviewVideoSession getVideoSession(Long processId) {
        return requireVideoSessionByProcess(processId);
    }

    @Override
    @Transactional
    public InterviewVideoSession getDownloadableVideoSession(Long processId) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        if (videoMergeService.canMerge(session) && !isReadableFile(session.getMergedRecordingPath())) {
            videoMergeService.mergeRecordings(session);
            videoSessionMapper.updateById(session);
        }
        return session;
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
                try {
                    videoMergeService.mergeRecordings(session);
                } catch (BusinessException ex) {
                    session.setSessionStatus("RECORDED");
                    videoSessionMapper.updateById(session);
                    markVideoWaitingApproval(session.getProcessId());
                    return toVideoSignalVO(session);
                }
                session.setSessionStatus("RECORDED");
                markVideoWaitingApproval(session.getProcessId());
            } else if (!StrUtil.equals(session.getSessionStatus(), "END_REQUESTED")) {
                session.setSessionStatus("END_REQUESTED");
            }
            videoSessionMapper.updateById(session);
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
        return vo;
    }

    @Override
    @Transactional
    public InterviewVO reportAntiCheatEvent(AntiCheatEventRequest request, Long intervieweeUserId, String intervieweeName) {
        InterviewProcess process = requireIntervieweeProcess(request.getProcessId(), intervieweeUserId);
        boolean switchEvent = isSwitchEvent(request.getEventType());
        if (switchEvent && StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            int count = Objects.requireNonNullElse(process.getAntiCheatSwitchCount(), 0) + 1;
            int limit = Math.max(Objects.requireNonNullElse(process.getAntiCheatSwitchLimit(), 5), 1);
            process.setAntiCheatSwitchCount(count);
            if (count >= limit) {
                process.setStageStatus("WAITING_APPROVAL");
                process.setProcessStatusView("切屏超限待人工审批");
                updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
                auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "ANTI_CHEAT_MANUAL_REVIEW", "INTERVIEW_PROCESS", String.valueOf(request.getProcessId()), "切屏" + count + "次达到阈值" + limit + "，转HR人工审批");
            }
            processMapper.updateById(process);
        }
        String detail = StrUtil.blankToDefault(request.getDetail(), "") + " eventType=" + request.getEventType();
        auditLogService.log(intervieweeUserId, displayName(intervieweeName, "面试者"), "INTERVIEWEE", "INTERVIEW", "ANTI_CHEAT_" + request.getEventType(), "INTERVIEW_PROCESS", String.valueOf(request.getProcessId()), abbreviate(detail));
        return toProcessVO(process);
    }

    private boolean isSwitchEvent(String eventType) {
        return Set.of("FULLSCREEN_EXIT", "TAB_HIDDEN", "WINDOW_BLUR").contains(StrUtil.blankToDefault(eventType, ""));
    }

    private void syncToPendingOnboarding(InterviewProcess process) {
        RecruitmentCandidate candidate = requireRecruitmentCandidate(process.getRecruitmentCandidateId());
        RecruitmentJob job = requireRecruitmentJob(process.getJobId());
        Employee existing = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getSourceCandidateId, candidate.getId())
                .last("LIMIT 1"));
        Employee employee = existing == null ? new Employee() : existing;
        if (existing == null) {
            employee.setId(nextId(employeeMapper.selectList(null).stream().map(Employee::getId).toList()));
            employee.setEmployeeCode("PENDING-" + candidate.getId());
        }
        employee.setFullName(candidate.getFullName());
        employee.setIdCardNo(StrUtil.blankToDefault(candidate.getIdCardNo(), "PENDING-ID-" + candidate.getId()));
        employee.setMobilePhone(candidate.getMobilePhone());
        employee.setEmail(candidate.getEmail());
        employee.setRecruitmentMajor(candidate.getMajor());
        employee.setPositionName(job.getJobTitle());
        employee.setDepartmentId(1L);
        employee.setBankAccountNo(StrUtil.blankToDefault(employee.getBankAccountNo(), "PENDING" + candidate.getId()));
        employee.setBankName(StrUtil.blankToDefault(employee.getBankName(), "待补充"));
        employee.setHireDate(LocalDate.now());
        employee.setEmploymentStatus(EmploymentStatus.PENDING_ONBOARDING.getCode());
        employee.setSourceCandidateId(candidate.getId());
        employee.setInterviewStageStatus("线下面");
        employee.setSourceChannel("RECRUITMENT_INTERVIEW");
        employee.setNotes("面试三轮通过，待入职同步生成");
        if (existing == null) {
            employeeMapper.insert(employee);
        } else {
            employeeMapper.updateById(employee);
        }
    }

    private void updateCandidateStage(Long recruitmentCandidateId, String stage) {
        RecruitmentCandidate candidate = requireRecruitmentCandidate(recruitmentCandidateId);
        candidate.setInterviewStageStatus(StrUtil.blankToDefault(stage, "简历待查"));
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
        InterviewVideoSession entity = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .last("LIMIT 1"));
        if (entity == null) throw new BusinessException("视频面试会话不存在");
        return entity;
    }

    private InterviewVideoSession ensureVideoSession(Long processId, Long approverUserId, String approverName) {
        InterviewVideoSession existing = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .last("LIMIT 1"));
        if (existing != null) {
            resetVideoSession(existing, approverUserId, approverName);
            videoSessionMapper.updateById(existing);
            return existing;
        }
        InterviewVideoSession session = new InterviewVideoSession();
        session.setId(nextId(videoSessionMapper.selectList(null).stream().map(InterviewVideoSession::getId).toList()));
        session.setProcessId(processId);
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
        session.setRecordingPath(null);
        session.setRecordingFileName(null);
        session.setHrRecordingPath(null);
        session.setHrRecordingFileName(null);
        session.setIntervieweeRecordingPath(null);
        session.setIntervieweeRecordingFileName(null);
        session.setMergedRecordingPath(null);
        session.setMergedRecordingFileName(null);
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
        InterviewAiRecord record = new InterviewAiRecord();
        record.setId(nextId(aiRecordMapper.selectList(null).stream().map(InterviewAiRecord::getId).toList()));
        record.setProcessId(processId);
        record.setKnowledgePoint("题目生成异常");
        record.setQuestionContent("AI题目生成失败，请联系管理员检查知识库权重、LLM配置或接口状态。错误：" + abbreviate(ex.getMessage()));
        record.setSequenceNo(nextSequence(processId));
        aiRecordMapper.insert(record);
    }

    private synchronized void generateNextQuestion(InterviewProcess process) {
        InterviewJobKnowledgeWeight weight = pickKnowledgeWeight(process);
        InterviewAiRecord record = new InterviewAiRecord();
        record.setId(nextId(aiRecordMapper.selectList(null).stream().map(InterviewAiRecord::getId).toList()));
        record.setProcessId(process.getId());
        record.setKnowledgeBaseId(weight == null ? null : weight.getKnowledgeBaseId());
        record.setKnowledgePoint(weight == null ? "通用沟通" : loadKnowledgeBaseName(weight.getKnowledgeBaseId()));
        record.setQuestionContent(callLlmQuestion(record.getKnowledgePoint(), loadKnowledgeMaterials(weight == null ? null : weight.getKnowledgeBaseId()), loadJobRequirements(process)));
        record.setSequenceNo(nextSequence(process.getId()));
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

    private synchronized void generateNextQuestion(InterviewProcess process, String nextQuestion) {
        if (StrUtil.isBlank(nextQuestion)) {
            generateNextQuestion(process);
            return;
        }
        InterviewJobKnowledgeWeight weight = pickKnowledgeWeight(process);
        InterviewAiRecord record = new InterviewAiRecord();
        record.setId(nextId(aiRecordMapper.selectList(null).stream().map(InterviewAiRecord::getId).toList()));
        record.setProcessId(process.getId());
        record.setKnowledgeBaseId(weight == null ? null : weight.getKnowledgeBaseId());
        record.setKnowledgePoint(weight == null ? "通用沟通" : loadKnowledgeBaseName(weight.getKnowledgeBaseId()));
        record.setQuestionContent(nextQuestion.replace("\n", " ").trim());
        record.setSequenceNo(nextSequence(process.getId()));
        aiRecordMapper.insert(record);
    }

    private LlmEvaluation callLlmEvaluation(String question, String answer, String topic, String materials, String jobRequirements, String role, boolean needNextQuestion) {
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
                + "\n下一题必须基于知识库材料并结合岗位要求，可以自然改写和语义修饰，不必与知识点原句一模一样，但不能引入材料和岗位要求外的知识点。"
                + "\n本格式要求优先于旧配置中的'只返回整数'类要求，不能只输出分数，也不能只输出问题。";
        String response = callOpenAiChat(config, systemPrompt, userPrompt);
        return parseEvaluation(response);
    }

    private int parseScore(String response) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(StrUtil.blankToDefault(response, ""));
        if (!matcher.find()) {
            throw new BusinessException("LLM未返回有效分数");
        }
        return Integer.parseInt(matcher.group());
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
        cn.hutool.json.JSONObject payload = new cn.hutool.json.JSONObject();
        payload.set("model", config.getModelName());
        payload.set("messages", cn.hutool.json.JSONUtil.parseArray(List.of(
                new cn.hutool.json.JSONObject().set("role", "system").set("content", systemPrompt),
                new cn.hutool.json.JSONObject().set("role", "user").set("content", userPrompt)
        )));
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

    private int nextSequence(Long processId) {
        return aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>().eq(InterviewAiRecord::getProcessId, processId)).size() + 1;
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

    private InterviewVO toProcessVO(InterviewProcess entity) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setRecruitmentCandidateId(entity.getRecruitmentCandidateId());
        vo.setIntervieweeUserId(entity.getIntervieweeUserId());
        vo.setJobId(entity.getJobId());
        RecruitmentCandidate candidate = recruitmentCandidateMapper.selectById(entity.getRecruitmentCandidateId());
        if (candidate != null) {
            vo.setCandidateName(candidate.getFullName());
        }
        RecruitmentJob job = recruitmentJobMapper.selectById(entity.getJobId());
        if (job != null) {
            vo.setQuestionTitle(job.getJobTitle());
        }
        vo.setCurrentStage(entity.getCurrentStage());
        vo.setStageStatus(entity.getStageStatus());
        vo.setOverallStatus(entity.getOverallStatus());
        vo.setAiThresholdScore(entity.getAiThresholdScore());
        vo.setAiAverageScore(entity.getAiAverageScore());
        vo.setAiMinQuestionRounds(entity.getAiMinQuestionRounds());
        vo.setAiMaxQuestionRounds(entity.getAiMaxQuestionRounds());
        vo.setAntiCheatSwitchLimit(entity.getAntiCheatSwitchLimit());
        vo.setAntiCheatSwitchCount(entity.getAntiCheatSwitchCount());
        vo.setVideoApproved(entity.getVideoApproved());
        vo.setOnsiteApproved(entity.getOnsiteApproved());
        vo.setApprovedHrUserId(entity.getApprovedHrUserId());
        vo.setApprovedHrName(entity.getApprovedHrName());
        vo.setProcessStatusView(entity.getProcessStatusView());
        vo.setRemark(entity.getRemark());
        fillVideoSessionSummary(vo, entity.getId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InterviewVO toAiRecordVO(InterviewAiRecord entity, InterviewProcess process) {
        InterviewVO vo = new InterviewVO();
        vo.setId(entity.getId());
        vo.setProcessId(entity.getProcessId());
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
        return vo;
    }

    private void fillVideoSessionSummary(InterviewVO vo, Long processId) {
        InterviewVideoSession session = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .last("LIMIT 1"));
        if (session == null) {
            return;
        }
        vo.setVideoSerialNo(session.getVideoSerialNo());
        vo.setVideoJoinLink(session.getVideoJoinLink());
        vo.setIntervieweeJoinTime(session.getIntervieweeJoinTime());
        vo.setHrJoinTime(session.getHrJoinTime());
        vo.setRecordingPath(StrUtil.blankToDefault(session.getMergedRecordingPath(), session.getRecordingPath()));
        vo.setRecordingFileName(StrUtil.blankToDefault(session.getMergedRecordingFileName(), session.getRecordingFileName()));
        vo.setSessionStatus(session.getSessionStatus());
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
        return vo;
    }

    private String currentIceCandidates(String sessionDescription, String candidates) {
        if (StrUtil.isBlank(candidates)) {
            return candidates;
        }
        return java.util.Arrays.stream(candidates.split("\\n"))
                .filter(StrUtil::isNotBlank)
                .filter(item -> isRelayIceCandidate(item))
                .filter(item -> isCurrentIceCandidate(sessionDescription, item))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private boolean isRelayIceCandidate(String iceCandidate) {
        return StrUtil.contains(iceCandidate, " typ relay ")
                || StrUtil.endWith(iceCandidate, " typ relay")
                || StrUtil.contains(iceCandidate, " typ relay\\r")
                || StrUtil.contains(iceCandidate, " typ relay\\n");
    }

    private String appendSignal(String existing, String value) {
        if (StrUtil.isBlank(existing)) {
            return value;
        }
        return existing + "\n" + value;
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

    private void markVideoWaitingApproval(Long processId) {
        InterviewProcess process = requireProcess(processId);
        if (StrUtil.equals(process.getCurrentStage(), "VIDEO") && StrUtil.equals(process.getOverallStatus(), "IN_PROGRESS")) {
            process.setStageStatus("WAITING_APPROVAL");
            process.setProcessStatusView("视频待审批");
            processMapper.updateById(process);
            updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
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

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
