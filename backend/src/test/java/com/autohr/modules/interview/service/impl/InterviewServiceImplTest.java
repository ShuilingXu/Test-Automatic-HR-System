package com.autohr.modules.interview.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.VideoSignalRequest;
import com.autohr.modules.interview.entity.InterviewProcess;
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
import com.autohr.modules.interview.service.VideoMergeService;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.autohr.modules.system.service.SystemConfigService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test"), InterviewProcess.class);
    }

    @Mock InterviewKnowledgeBaseMapper knowledgeBaseMapper;
    @Mock InterviewKnowledgeItemMapper knowledgeItemMapper;
    @Mock InterviewJobKnowledgeWeightMapper jobKnowledgeWeightMapper;
    @Mock InterviewLlmConfigMapper llmConfigMapper;
    @Mock InterviewProcessMapper processMapper;
    @Mock InterviewProcessStageMapper processStageMapper;
    @Mock InterviewProcessTemplateMapper processTemplateMapper;
    @Mock InterviewProcessTemplateStageMapper processTemplateStageMapper;
    @Mock InterviewAiRecordMapper aiRecordMapper;
    @Mock InterviewVideoSessionMapper videoSessionMapper;
    @Mock RecruitmentCandidateMapper recruitmentCandidateMapper;
    @Mock RecruitmentJobMapper recruitmentJobMapper;
    @Mock DepartmentMapper departmentMapper;
    @Mock EmployeeMapper employeeMapper;
    @Mock AuditLogService auditLogService;
    @Mock VideoMergeService videoMergeService;
    @Mock S3ObjectStorageService s3ObjectStorageService;
    @Mock SystemConfigService systemConfigService;
    @Mock TransactionTemplate transactionTemplate;

    @InjectMocks
    InterviewServiceImpl service;

    @Test
    void rejectsApprovalWhenAnotherRequestAlreadyClaimedTheTransition() {
        InterviewProcess process = new InterviewProcess();
        process.setId(42L);
        process.setOverallStatus("IN_PROGRESS");
        process.setCurrentStage("AI");
        process.setStageStatus("WAITING_APPROVAL");
        when(processMapper.selectById(42L)).thenReturn(process);
        when(processMapper.update(
                ArgumentMatchers.<InterviewProcess>isNull(),
                ArgumentMatchers.<Wrapper<InterviewProcess>>any())).thenReturn(0);

        InterviewDecisionRequest request = new InterviewDecisionRequest();
        request.setApproved(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approveAiToVideo(42L, request));

        assertTrue(error.getMessage().contains("状态已变化"));
        verify(processMapper, never()).updateById(any());
        verify(videoSessionMapper, never()).insert(any());
    }

    @Test
    void rejectsOversizedIceCandidateBeforeDatabaseAccess() {
        VideoSignalRequest request = new VideoSignalRequest();
        request.setIceCandidate("x".repeat(4097));

        assertThrows(BusinessException.class, () -> service.addHrIceCandidate(42L, request));

        verify(processMapper, never()).selectById(any());
        verify(videoSessionMapper, never()).selectOne(any());
    }
}
