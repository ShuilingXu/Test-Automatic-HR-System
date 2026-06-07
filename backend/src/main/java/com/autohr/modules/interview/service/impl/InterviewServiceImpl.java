package com.autohr.modules.interview.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.enums.EmploymentStatus;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.JobKnowledgeWeightSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeBaseSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeItemSaveRequest;
import com.autohr.modules.interview.dto.LlmConfigSaveRequest;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;
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
import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

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
    public List<InterviewVO> listKnowledgeItems(Long knowledgeBaseId, String keyword) {
        return knowledgeItemMapper.selectList(new LambdaQueryWrapper<InterviewKnowledgeItem>()
                .eq(knowledgeBaseId != null, InterviewKnowledgeItem::getKnowledgeBaseId, knowledgeBaseId)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewKnowledgeItem::getKnowledgePoint, keyword)
                        .or().like(InterviewKnowledgeItem::getKnowledgeContent, keyword))
                .orderByAsc(InterviewKnowledgeItem::getId)).stream().map(this::toKnowledgeItemVO).toList();
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
    public InterviewVO saveLlmConfig(LlmConfigSaveRequest request) {
        InterviewLlmConfig entity = request.getId() == null ? new InterviewLlmConfig() : requireLlmConfig(request.getId());
        BeanUtils.copyProperties(request, entity);
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
    public InterviewVO startInterviewProcess(StartInterviewProcessRequest request) {
        RecruitmentCandidate candidate = requireRecruitmentCandidate(request.getRecruitmentCandidateId());
        requireRecruitmentJob(request.getJobId());
        InterviewProcess process = new InterviewProcess();
        process.setId(nextId(processMapper.selectList(null).stream().map(InterviewProcess::getId).toList()));
        process.setRecruitmentCandidateId(request.getRecruitmentCandidateId());
        process.setIntervieweeUserId(request.getIntervieweeUserId());
        process.setJobId(request.getJobId());
        process.setCurrentStage("AI");
        process.setStageStatus("IN_PROGRESS");
        process.setOverallStatus("IN_PROGRESS");
        process.setAiThresholdScore(Objects.requireNonNullElse(request.getAiThresholdScore(), 7));
        process.setVideoApproved(0);
        process.setOnsiteApproved(0);
        process.setProcessStatusView("AI面");
        processMapper.insert(process);
        candidate.setInterviewProcessId(process.getId());
        candidate.setInterviewStageStatus("AI面");
        candidate.setApplicationStatus("INTERVIEWING");
        recruitmentCandidateMapper.updateById(candidate);
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
    @Transactional
    public InterviewVO submitAiAnswer(AiAnswerRequest request) {
        InterviewProcess process = requireProcess(request.getProcessId());
        if (!StrUtil.equals(process.getCurrentStage(), "AI")) {
            throw new BusinessException("当前流程不在AI面试阶段");
        }
        InterviewJobKnowledgeWeight weight = pickKnowledgeWeight(process.getJobId());
        InterviewAiRecord record = new InterviewAiRecord();
        record.setId(nextId(aiRecordMapper.selectList(null).stream().map(InterviewAiRecord::getId).toList()));
        record.setProcessId(process.getId());
        record.setKnowledgeBaseId(weight == null ? null : weight.getKnowledgeBaseId());
        record.setKnowledgePoint(weight == null ? "通用沟通" : weight.getKnowledgePoint());
        record.setQuestionContent("请结合“" + record.getKnowledgePoint() + "”介绍你的理解与实际应用。");
        record.setAnswerContent(request.getAnswerContent());
        int interviewerScore = mockScore(request.getAnswerContent(), 6);
        int scorerScore = mockScore(request.getAnswerContent(), 7);
        int averageScore = Math.round((interviewerScore + scorerScore) / 2.0f);
        record.setInterviewerScore(interviewerScore);
        record.setScorerScore(scorerScore);
        record.setAverageScore(averageScore);
        record.setSequenceNo(nextSequence(process.getId()));
        aiRecordMapper.insert(record);

        int average = aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>().eq(InterviewAiRecord::getProcessId, process.getId()))
                .stream().map(InterviewAiRecord::getAverageScore).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int count = Math.max(aiRecordMapper.selectCount(new LambdaQueryWrapper<InterviewAiRecord>().eq(InterviewAiRecord::getProcessId, process.getId())).intValue(), 1);
        int currentAverage = Math.round(average / (float) count);
        process.setAiAverageScore(currentAverage);
        if (currentAverage >= process.getAiThresholdScore()) {
            process.setStageStatus("PASSED");
            process.setCurrentStage("VIDEO");
            process.setProcessStatusView("视频面");
        }
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        return toAiRecordVO(record, process);
    }

    @Override
    public List<InterviewVO> listAiRecords(Long processId) {
        return aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>()
                .eq(processId != null, InterviewAiRecord::getProcessId, processId)
                .orderByAsc(InterviewAiRecord::getSequenceNo)
                .orderByAsc(InterviewAiRecord::getId)).stream().map(item -> toAiRecordVO(item, null)).toList();
    }

    @Override
    @Transactional
    public InterviewVO createVideoSession(Long processId, Long approverUserId, String approverName) {
        InterviewProcess process = requireProcess(processId);
        if (!StrUtil.equals(process.getCurrentStage(), "VIDEO")) {
            throw new BusinessException("当前流程不在视频面试阶段");
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
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO intervieweeJoinVideo(Long processId) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        session.setIntervieweeJoinTime(LocalDateTime.now());
        if (session.getStartTime() == null) {
            session.setStartTime(LocalDateTime.now());
        }
        session.setSessionStatus("INTERVIEWEE_JOINED");
        videoSessionMapper.updateById(session);
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO hrJoinVideo(Long processId, Long approverUserId, String approverName) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        session.setApproverUserId(approverUserId);
        session.setApproverName(approverName);
        session.setHrJoinTime(LocalDateTime.now());
        session.setStartTime(session.getStartTime() == null ? LocalDateTime.now() : session.getStartTime());
        session.setSessionStatus("HR_JOINED");
        videoSessionMapper.updateById(session);
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO completeVideoSession(Long processId, String recordingPath) {
        InterviewVideoSession session = requireVideoSessionByProcess(processId);
        session.setEndTime(LocalDateTime.now());
        session.setRecordingPath(recordingPath);
        session.setSessionStatus("WAITING_APPROVAL");
        videoSessionMapper.updateById(session);
        return toVideoSessionVO(session);
    }

    @Override
    @Transactional
    public InterviewVO approveAiToVideo(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        if (request.getApproved() == 1) {
            process.setCurrentStage("VIDEO");
            process.setStageStatus("READY");
            process.setProcessStatusView("视频面");
        } else {
            process.setOverallStatus("REJECTED");
            process.setStageStatus("REJECTED");
        }
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO approveVideoToOnsite(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
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
        }
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), process.getProcessStatusView());
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO decideOnsite(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
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
        return toProcessVO(process);
    }

    @Override
    @Transactional
    public InterviewVO terminateProcess(Long processId, InterviewDecisionRequest request) {
        InterviewProcess process = requireProcess(processId);
        process.setOverallStatus("TERMINATED");
        process.setStageStatus("TERMINATED");
        process.setProcessStatusView("已终止");
        process.setApprovedHrUserId(request.getApproverUserId());
        process.setApprovedHrName(request.getApproverName());
        processMapper.updateById(process);
        updateCandidateStage(process.getRecruitmentCandidateId(), "已终止");
        return toProcessVO(process);
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

    private InterviewVideoSession requireVideoSessionByProcess(Long processId) {
        InterviewVideoSession entity = videoSessionMapper.selectOne(new LambdaQueryWrapper<InterviewVideoSession>()
                .eq(InterviewVideoSession::getProcessId, processId)
                .last("LIMIT 1"));
        if (entity == null) throw new BusinessException("视频面试会话不存在");
        return entity;
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

    private InterviewJobKnowledgeWeight pickKnowledgeWeight(Long jobId) {
        return jobKnowledgeWeightMapper.selectOne(new LambdaQueryWrapper<InterviewJobKnowledgeWeight>()
                .eq(InterviewJobKnowledgeWeight::getJobId, jobId)
                .orderByDesc(InterviewJobKnowledgeWeight::getWeight)
                .last("LIMIT 1"));
    }

    private int nextSequence(Long processId) {
        return aiRecordMapper.selectList(new LambdaQueryWrapper<InterviewAiRecord>().eq(InterviewAiRecord::getProcessId, processId)).size() + 1;
    }

    private int mockScore(String answer, int base) {
        int lengthScore = Math.min(StrUtil.length(answer) / 20, 3);
        return Math.min(10, base + lengthScore);
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
        vo.setKnowledgePoint(entity.getKnowledgePoint());
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
        vo.setApiKeyMasked(entity.getApiKeyMasked());
        vo.setModelName(entity.getModelName());
        vo.setPromptTemplate(entity.getPromptTemplate());
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
        vo.setCurrentStage(entity.getCurrentStage());
        vo.setStageStatus(entity.getStageStatus());
        vo.setOverallStatus(entity.getOverallStatus());
        vo.setAiThresholdScore(entity.getAiThresholdScore());
        vo.setAiAverageScore(entity.getAiAverageScore());
        vo.setVideoApproved(entity.getVideoApproved());
        vo.setOnsiteApproved(entity.getOnsiteApproved());
        vo.setApprovedHrUserId(entity.getApprovedHrUserId());
        vo.setApprovedHrName(entity.getApprovedHrName());
        vo.setProcessStatusView(entity.getProcessStatusView());
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
        vo.setRecordingPath(entity.getRecordingPath());
        vo.setSessionStatus(entity.getSessionStatus());
        return vo;
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
