package com.autohr.modules.interview.controller;

import com.autohr.common.api.ApiResponse;
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
import com.autohr.modules.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/hr/knowledge-bases")
    public ApiResponse<InterviewVO> saveKnowledgeBase(@Valid @RequestBody KnowledgeBaseSaveRequest request) {
        return ApiResponse.success(interviewService.saveKnowledgeBase(request));
    }

    @GetMapping("/hr/knowledge-bases")
    public ApiResponse<List<InterviewVO>> listKnowledgeBases(@RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.success(interviewService.listKnowledgeBases(status, keyword));
    }

    @PostMapping("/hr/knowledge-bases/{id}/delete")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable Long id) {
        interviewService.deleteKnowledgeBase(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/hr/knowledge-items")
    public ApiResponse<InterviewVO> saveKnowledgeItem(@Valid @RequestBody KnowledgeItemSaveRequest request) {
        return ApiResponse.success(interviewService.saveKnowledgeItem(request));
    }

    @GetMapping("/hr/knowledge-items")
    public ApiResponse<List<InterviewVO>> listKnowledgeItems(@RequestParam(required = false) Long knowledgeBaseId,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.success(interviewService.listKnowledgeItems(knowledgeBaseId, keyword));
    }

    @PostMapping("/hr/knowledge-items/{id}/delete")
    public ApiResponse<Void> deleteKnowledgeItem(@PathVariable Long id) {
        interviewService.deleteKnowledgeItem(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/hr/job-knowledge-weights")
    public ApiResponse<InterviewVO> saveJobKnowledgeWeight(@Valid @RequestBody JobKnowledgeWeightSaveRequest request) {
        return ApiResponse.success(interviewService.saveJobKnowledgeWeight(request));
    }

    @GetMapping("/hr/job-knowledge-weights")
    public ApiResponse<List<InterviewVO>> listJobKnowledgeWeights(@RequestParam(required = false) Long jobId) {
        return ApiResponse.success(interviewService.listJobKnowledgeWeights(jobId));
    }

    @PostMapping("/hr/job-knowledge-weights/{id}/delete")
    public ApiResponse<Void> deleteJobKnowledgeWeight(@PathVariable Long id) {
        interviewService.deleteJobKnowledgeWeight(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/it/llm-configs")
    public ApiResponse<InterviewVO> saveLlmConfig(@Valid @RequestBody LlmConfigSaveRequest request) {
        return ApiResponse.success(interviewService.saveLlmConfig(request));
    }

    @GetMapping("/it/llm-configs")
    public ApiResponse<List<InterviewVO>> listLlmConfigs(@RequestParam(required = false) String modelRole,
                                                         @RequestParam(required = false) Integer status) {
        return ApiResponse.success(interviewService.listLlmConfigs(modelRole, status));
    }

    @PostMapping("/it/llm-configs/{id}/delete")
    public ApiResponse<Void> deleteLlmConfig(@PathVariable Long id) {
        interviewService.deleteLlmConfig(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/hr/processes")
    public ApiResponse<InterviewVO> startProcess(@Valid @RequestBody StartInterviewProcessRequest request) {
        return ApiResponse.success(interviewService.startInterviewProcess(request));
    }

    @GetMapping("/hr/processes")
    public ApiResponse<List<InterviewVO>> listProcesses(@RequestParam(required = false) String overallStatus,
                                                        @RequestParam(required = false) String stageStatus,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.success(interviewService.listProcesses(overallStatus, stageStatus, keyword));
    }

    @GetMapping("/interviewee/process/{processId}")
    public ApiResponse<InterviewVO> getIntervieweeProcess(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.getProcess(processId));
    }

    @GetMapping("/interviewee/next-question/{processId}")
    public ApiResponse<InterviewVO> getNextQuestion(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.getNextAiQuestion(processId));
    }

    @PostMapping("/interviewee/ai-answer")
    public ApiResponse<InterviewVO> submitAiAnswer(@Valid @RequestBody AiAnswerRequest request) {
        return ApiResponse.success(interviewService.submitAiAnswer(request));
    }

    @GetMapping("/hr/ai-records")
    public ApiResponse<List<InterviewVO>> listAiRecords(@RequestParam Long processId) {
        return ApiResponse.success(interviewService.listAiRecords(processId));
    }

    @GetMapping("/interviewee/ai-records")
    public ApiResponse<List<InterviewVO>> listIntervieweeAiRecords(@RequestParam Long processId) {
        return ApiResponse.success(interviewService.listAiRecords(processId));
    }

    @PostMapping("/hr/video-session/{processId}")
    public ApiResponse<InterviewVO> createVideoSession(@PathVariable Long processId,
                                                       @RequestParam(required = false) Long approverUserId,
                                                       @RequestParam(required = false) String approverName) {
        return ApiResponse.success(interviewService.createVideoSession(processId, approverUserId, approverName));
    }

    @PostMapping("/interviewee/video-join/{processId}")
    public ApiResponse<InterviewVO> intervieweeJoin(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.intervieweeJoinVideo(processId));
    }

    @PostMapping("/hr/video-join/{processId}")
    public ApiResponse<InterviewVO> hrJoin(@PathVariable Long processId,
                                           @RequestParam(required = false) Long approverUserId,
                                           @RequestParam(required = false) String approverName) {
        return ApiResponse.success(interviewService.hrJoinVideo(processId, approverUserId, approverName));
    }

    @PostMapping("/hr/video-complete/{processId}")
    public ApiResponse<InterviewVO> completeVideo(@PathVariable Long processId,
                                                  @RequestParam(required = false) String recordingPath) {
        return ApiResponse.success(interviewService.completeVideoSession(processId, recordingPath));
    }

    @PostMapping("/hr/video-offer/{processId}")
    public ApiResponse<VideoSignalVO> publishOffer(@PathVariable Long processId,
                                                   @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.publishHrOffer(processId, request));
    }

    @PostMapping("/interviewee/video-answer/{processId}")
    public ApiResponse<VideoSignalVO> submitAnswer(@PathVariable Long processId,
                                                   @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.submitIntervieweeAnswer(processId, request));
    }

    @PostMapping("/hr/video-ice/{processId}")
    public ApiResponse<VideoSignalVO> addHrIce(@PathVariable Long processId,
                                               @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.addHrIceCandidate(processId, request));
    }

    @PostMapping("/interviewee/video-ice/{processId}")
    public ApiResponse<VideoSignalVO> addIntervieweeIce(@PathVariable Long processId,
                                                        @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.addIntervieweeIceCandidate(processId, request));
    }

    @GetMapping("/interviewee/video-state/{processId}")
    public ApiResponse<VideoSignalVO> getVideoState(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.getVideoSignalState(processId));
    }

    @PostMapping("/interviewee/video-recording/{processId}")
    public ApiResponse<VideoSignalVO> uploadRecording(@PathVariable Long processId,
                                                      @RequestParam String originalFileName,
                                                      @RequestParam(required = false) String contentType,
                                                      @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(interviewService.uploadRecording(processId, originalFileName, contentType, file));
    }

    @PostMapping("/hr/approve-ai/{processId}")
    public ApiResponse<InterviewVO> approveAi(@PathVariable Long processId,
                                              @Valid @RequestBody InterviewDecisionRequest request) {
        return ApiResponse.success(interviewService.approveAiToVideo(processId, request));
    }

    @PostMapping("/hr/approve-video/{processId}")
    public ApiResponse<InterviewVO> approveVideo(@PathVariable Long processId,
                                                 @Valid @RequestBody InterviewDecisionRequest request) {
        return ApiResponse.success(interviewService.approveVideoToOnsite(processId, request));
    }

    @PostMapping("/hr/approve-onsite/{processId}")
    public ApiResponse<InterviewVO> approveOnsite(@PathVariable Long processId,
                                                  @Valid @RequestBody InterviewDecisionRequest request) {
        return ApiResponse.success(interviewService.decideOnsite(processId, request));
    }

    @PostMapping("/hr/terminate/{processId}")
    public ApiResponse<InterviewVO> terminate(@PathVariable Long processId,
                                              @Valid @RequestBody InterviewDecisionRequest request) {
        return ApiResponse.success(interviewService.terminateProcess(processId, request));
    }
}
