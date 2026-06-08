package com.autohr.modules.interview.service;

import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.JobKnowledgeWeightSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeBaseSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeItemSaveRequest;
import com.autohr.modules.interview.dto.LlmConfigSaveRequest;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;
import com.autohr.modules.interview.dto.VideoSignalRequest;
import com.autohr.modules.interview.dto.VideoSignalVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InterviewService {
    InterviewVO saveKnowledgeBase(KnowledgeBaseSaveRequest request);
    List<InterviewVO> listKnowledgeBases(Integer status, String keyword);
    void deleteKnowledgeBase(Long id);
    InterviewVO saveKnowledgeItem(KnowledgeItemSaveRequest request);
    List<InterviewVO> listKnowledgeItems(Long knowledgeBaseId, String keyword);
    void deleteKnowledgeItem(Long id);
    InterviewVO saveJobKnowledgeWeight(JobKnowledgeWeightSaveRequest request);
    List<InterviewVO> listJobKnowledgeWeights(Long jobId);
    void deleteJobKnowledgeWeight(Long id);
    InterviewVO saveLlmConfig(LlmConfigSaveRequest request);
    List<InterviewVO> listLlmConfigs(String modelRole, Integer status);
    void deleteLlmConfig(Long id);
    InterviewVO startInterviewProcess(StartInterviewProcessRequest request);
    List<InterviewVO> listProcesses(String overallStatus, String stageStatus, String keyword);
    InterviewVO getProcess(Long processId);
    InterviewVO getNextAiQuestion(Long processId);
    InterviewVO submitAiAnswer(AiAnswerRequest request);
    List<InterviewVO> listAiRecords(Long processId);
    InterviewVO createVideoSession(Long processId, Long approverUserId, String approverName);
    InterviewVO intervieweeJoinVideo(Long processId);
    InterviewVO hrJoinVideo(Long processId, Long approverUserId, String approverName);
    InterviewVO completeVideoSession(Long processId, String recordingPath);
    VideoSignalVO publishHrOffer(Long processId, VideoSignalRequest request);
    VideoSignalVO submitIntervieweeAnswer(Long processId, VideoSignalRequest request);
    VideoSignalVO addHrIceCandidate(Long processId, VideoSignalRequest request);
    VideoSignalVO addIntervieweeIceCandidate(Long processId, VideoSignalRequest request);
    VideoSignalVO getVideoSignalState(Long processId);
    VideoSignalVO uploadRecording(Long processId, String originalFileName, String contentType, MultipartFile file);
    InterviewVO approveAiToVideo(Long processId, InterviewDecisionRequest request);
    InterviewVO approveVideoToOnsite(Long processId, InterviewDecisionRequest request);
    InterviewVO decideOnsite(Long processId, InterviewDecisionRequest request);
    InterviewVO terminateProcess(Long processId, InterviewDecisionRequest request);
}
