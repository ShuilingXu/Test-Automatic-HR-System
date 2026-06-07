package com.autohr.modules.interview.service;

import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.JobKnowledgeWeightSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeBaseSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeItemSaveRequest;
import com.autohr.modules.interview.dto.LlmConfigSaveRequest;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;

import java.util.List;

public interface InterviewService {
    InterviewVO saveKnowledgeBase(KnowledgeBaseSaveRequest request);
    List<InterviewVO> listKnowledgeBases(Integer status, String keyword);
    InterviewVO saveKnowledgeItem(KnowledgeItemSaveRequest request);
    List<InterviewVO> listKnowledgeItems(Long knowledgeBaseId, String keyword);
    InterviewVO saveJobKnowledgeWeight(JobKnowledgeWeightSaveRequest request);
    List<InterviewVO> listJobKnowledgeWeights(Long jobId);
    InterviewVO saveLlmConfig(LlmConfigSaveRequest request);
    List<InterviewVO> listLlmConfigs(String modelRole, Integer status);
    InterviewVO startInterviewProcess(StartInterviewProcessRequest request);
    List<InterviewVO> listProcesses(String overallStatus, String stageStatus, String keyword);
    InterviewVO getProcess(Long processId);
    InterviewVO submitAiAnswer(AiAnswerRequest request);
    List<InterviewVO> listAiRecords(Long processId);
    InterviewVO createVideoSession(Long processId, Long approverUserId, String approverName);
    InterviewVO intervieweeJoinVideo(Long processId);
    InterviewVO hrJoinVideo(Long processId, Long approverUserId, String approverName);
    InterviewVO completeVideoSession(Long processId, String recordingPath);
    InterviewVO approveAiToVideo(Long processId, InterviewDecisionRequest request);
    InterviewVO approveVideoToOnsite(Long processId, InterviewDecisionRequest request);
    InterviewVO decideOnsite(Long processId, InterviewDecisionRequest request);
    InterviewVO terminateProcess(Long processId, InterviewDecisionRequest request);
}
