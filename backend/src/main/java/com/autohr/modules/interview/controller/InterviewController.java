package com.autohr.modules.interview.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.file.FileDownloadSupport;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.AntiCheatEventRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.IceServerVO;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final AuthService authService;

    @Value("${interview.webrtc.stun-urls:stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478}")
    private String stunUrls;

    @Value("${interview.webrtc.turn-urls:}")
    private String turnUrls;

    @Value("${interview.webrtc.turn-username:}")
    private String turnUsername;

    @Value("${interview.webrtc.turn-credential:}")
    private String turnCredential;

    @Value("${interview.security.disable-devtools-shortcuts:true}")
    private boolean disableDevtoolsShortcuts;

    @GetMapping("/runtime-config")
    public ApiResponse<Map<String, Boolean>> getRuntimeConfig() {
        return ApiResponse.success(Map.of("disableDevtoolsShortcuts", disableDevtoolsShortcuts));
    }

    @GetMapping("/ice-servers")
    public ApiResponse<List<IceServerVO>> getIceServers() {
        List<IceServerVO> servers = new java.util.ArrayList<>();
        List<String> stun = splitUrls(stunUrls);
        if (!stun.isEmpty()) {
            servers.add(new IceServerVO(stun, null, null));
        }
        List<String> turn = splitUrls(turnUrls);
        if (!turn.isEmpty()) {
            servers.add(new IceServerVO(turn, blankToNull(turnUsername), blankToNull(turnCredential)));
        }
        return ApiResponse.success(servers);
    }

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

    @PostMapping("/hr/knowledge-items/import-csv")
    public ApiResponse<Map<String, Integer>> importKnowledgeItems(@RequestParam Long knowledgeBaseId,
                                                                  @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(Map.of("imported", interviewService.importKnowledgeItems(knowledgeBaseId, file)));
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
    public ApiResponse<InterviewVO> getIntervieweeProcess(Authentication authentication,
                                                          @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.getIntervieweeProcess(processId, current.getId()));
    }

    @GetMapping("/interviewee/next-question/{processId}")
    public ApiResponse<InterviewVO> getNextQuestion(Authentication authentication,
                                                    @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.getIntervieweeNextAiQuestion(processId, current.getId()));
    }

    @PostMapping("/interviewee/ai-answer")
    public ApiResponse<InterviewVO> submitAiAnswer(Authentication authentication,
                                                    @Valid @RequestBody AiAnswerRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.submitIntervieweeAiAnswer(request, current.getId()));
    }

    @PostMapping("/interviewee/ai-answer-stream")
    public SseEmitter submitAiAnswerStream(Authentication authentication,
                                           @Valid @RequestBody AiAnswerRequest request) {
        SessionUserVO current = currentUser(authentication);
        SseEmitter emitter = new SseEmitter(180000L);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                interviewService.submitAiAnswerStreaming(request, current.getId(), emitter);
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(java.util.Map.of("message", ex.getMessage() == null ? "处理失败" : ex.getMessage())));
                } catch (Exception ignored) {}
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @PostMapping("/interviewee/anti-cheat-event")
    public ApiResponse<InterviewVO> reportAntiCheatEvent(Authentication authentication,
                                                         @Valid @RequestBody AntiCheatEventRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.reportAntiCheatEvent(request, current.getId(), current.getDisplayName()));
    }

    @PostMapping("/interviewee/ai-recording/{processId}")
    public ApiResponse<InterviewVO> uploadAiRecording(Authentication authentication,
                                                      @PathVariable Long processId,
                                                      @RequestParam String originalFileName,
                                                      @RequestParam(required = false) String contentType,
                                                      @RequestParam("file") MultipartFile file) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.uploadAiRecording(processId, current.getId(), current.getDisplayName(), originalFileName, contentType, file));
    }

    @GetMapping("/hr/ai-recording/{processId}")
    public ResponseEntity<Resource> downloadAiRecording(@PathVariable Long processId) {
        var vo = interviewService.getProcess(processId);
        if (vo.getAiRecordingPath() == null) {
            return ResponseEntity.notFound().build();
        }
        return FileDownloadSupport.buildInlineResponse(vo.getAiRecordingPath(), UploadPaths.RECORDING_DIR, vo.getAiRecordingFileName(), "video/webm", "AI面试录制文件不可访问");
    }

    @GetMapping("/hr/ai-records")
    public ApiResponse<List<InterviewVO>> listAiRecords(@RequestParam Long processId) {
        return ApiResponse.success(interviewService.listAiRecords(processId));
    }

    @GetMapping("/interviewee/ai-records")
    public ApiResponse<List<InterviewVO>> listIntervieweeAiRecords(Authentication authentication,
                                                                   @RequestParam Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.listIntervieweeAiRecords(processId, current.getId()));
    }

    @PostMapping("/hr/video-session/{processId}")
    public ApiResponse<InterviewVO> createVideoSession(Authentication authentication,
                                                       @PathVariable Long processId,
                                                       @RequestParam(required = false) Long approverUserId,
                                                       @RequestParam(required = false) String approverName) {
        SessionUserVO current = currentUser(authentication);
        approverUserId = approverUserId == null ? current.getId() : approverUserId;
        approverName = approverName == null || approverName.isBlank() ? current.getDisplayName() : approverName;
        return ApiResponse.success(interviewService.createVideoSession(processId, approverUserId, approverName));
    }

    @PostMapping("/interviewee/video-join/{processId}")
    public ApiResponse<InterviewVO> intervieweeJoin(Authentication authentication,
                                                    @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.intervieweeJoinVideo(processId, current.getId(), current.getDisplayName()));
    }

    @PostMapping("/hr/video-join/{processId}")
    public ApiResponse<InterviewVO> hrJoin(Authentication authentication,
                                           @PathVariable Long processId,
                                           @RequestParam(required = false) Long approverUserId,
                                           @RequestParam(required = false) String approverName) {
        SessionUserVO current = currentUser(authentication);
        approverUserId = approverUserId == null ? current.getId() : approverUserId;
        approverName = approverName == null || approverName.isBlank() ? current.getDisplayName() : approverName;
        return ApiResponse.success(interviewService.hrJoinVideo(processId, approverUserId, approverName));
    }

    @PostMapping("/hr/video-complete/{processId}")
    public ApiResponse<InterviewVO> completeVideo(@PathVariable Long processId,
                                                    @RequestParam(required = false) String recordingPath) {
        return ApiResponse.success(interviewService.completeVideoSession(processId, recordingPath));
    }

    @PostMapping("/interviewee/video-complete/{processId}")
    public ApiResponse<InterviewVO> intervieweeCompleteVideo(Authentication authentication,
                                                             @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.requestIntervieweeVideoEnd(processId, current.getId()));
    }

    @PostMapping("/hr/video-recording/{processId}")
    public ApiResponse<VideoSignalVO> uploadHrRecording(@PathVariable Long processId,
                                                         @RequestParam String originalFileName,
                                                         @RequestParam(required = false) String contentType,
                                                         @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(interviewService.uploadHrRecording(processId, originalFileName, contentType, file));
    }

    @PostMapping("/hr/video-offer/{processId}")
    public ApiResponse<VideoSignalVO> publishOffer(@PathVariable Long processId,
                                                   @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.publishHrOffer(processId, request));
    }

    @GetMapping("/hr/video-state/{processId}")
    public ApiResponse<VideoSignalVO> getHrVideoState(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.getVideoSignalState(processId));
    }

    @PostMapping("/interviewee/video-answer/{processId}")
    public ApiResponse<VideoSignalVO> submitAnswer(Authentication authentication,
                                                   @PathVariable Long processId,
                                                   @RequestBody VideoSignalRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.submitIntervieweeAnswer(processId, request, current.getId(), current.getDisplayName()));
    }

    @PostMapping("/hr/video-ice/{processId}")
    public ApiResponse<VideoSignalVO> addHrIce(@PathVariable Long processId,
                                               @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.addHrIceCandidate(processId, request));
    }

    @PostMapping("/interviewee/video-ice/{processId}")
    public ApiResponse<VideoSignalVO> addIntervieweeIce(Authentication authentication,
                                                        @PathVariable Long processId,
                                                        @RequestBody VideoSignalRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.addIntervieweeIceCandidate(processId, request, current.getId(), current.getDisplayName()));
    }

    @GetMapping("/interviewee/video-state/{processId}")
    public ApiResponse<VideoSignalVO> getVideoState(Authentication authentication,
                                                    @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        interviewService.getIntervieweeProcess(processId, current.getId());
        return ApiResponse.success(interviewService.getVideoSignalState(processId));
    }

    @PostMapping("/interviewee/video-recording/{processId}")
    public ApiResponse<VideoSignalVO> uploadRecording(Authentication authentication,
                                                      @PathVariable Long processId,
                                                      @RequestParam String originalFileName,
                                                      @RequestParam(required = false) String contentType,
                                                      @RequestParam("file") MultipartFile file) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.uploadIntervieweeRecording(processId, current.getId(), current.getDisplayName(), originalFileName, contentType, file));
    }

    @GetMapping("/hr/video-recording/{processId}")
    public ResponseEntity<Resource> downloadRecording(@PathVariable Long processId) {
        var session = interviewService.getDownloadableVideoSession(processId);
        String path = session.getMergedRecordingPath() == null ? session.getRecordingPath() : session.getMergedRecordingPath();
        String fileName = session.getMergedRecordingFileName() == null ? session.getRecordingFileName() : session.getMergedRecordingFileName();
        return FileDownloadSupport.buildInlineResponse(path, UploadPaths.RECORDING_DIR, fileName, "video/webm", "录制文件不可访问");
    }

    @PostMapping("/hr/approve-ai/{processId}")
    public ApiResponse<InterviewVO> approveAi(Authentication authentication,
                                              @PathVariable Long processId,
                                              @Valid @RequestBody InterviewDecisionRequest request) {
        fillApprover(authentication, request);
        return ApiResponse.success(interviewService.approveAiToVideo(processId, request));
    }

    @PostMapping("/hr/approve-video/{processId}")
    public ApiResponse<InterviewVO> approveVideo(Authentication authentication,
                                                 @PathVariable Long processId,
                                                  @Valid @RequestBody InterviewDecisionRequest request) {
        fillApprover(authentication, request);
        return ApiResponse.success(interviewService.approveVideoToOnsite(processId, request));
    }

    @PostMapping("/hr/approve-onsite/{processId}")
    public ApiResponse<InterviewVO> approveOnsite(Authentication authentication,
                                                  @PathVariable Long processId,
                                                   @Valid @RequestBody InterviewDecisionRequest request) {
        fillApprover(authentication, request);
        return ApiResponse.success(interviewService.decideOnsite(processId, request));
    }

    @PostMapping("/hr/terminate/{processId}")
    public ApiResponse<InterviewVO> terminate(Authentication authentication,
                                               @PathVariable Long processId,
                                               @Valid @RequestBody InterviewDecisionRequest request) {
        fillApprover(authentication, request);
        return ApiResponse.success(interviewService.terminateProcess(processId, request));
    }

    @PostMapping("/hr/processes/{processId}/remark")
    public ApiResponse<InterviewVO> updateProcessRemark(Authentication authentication,
                                                        @PathVariable Long processId,
                                                        @RequestBody InterviewDecisionRequest request) {
        fillApprover(authentication, request);
        return ApiResponse.success(interviewService.updateProcessRemark(processId, request));
    }

    private SessionUserVO currentUser(Authentication authentication) {
        return authService.loadUserByUsername(authentication.getName());
    }

    private List<String> splitUrls(String urls) {
        if (urls == null || urls.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(urls.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void fillApprover(Authentication authentication, InterviewDecisionRequest request) {
        SessionUserVO current = currentUser(authentication);
        request.setApproverUserId(current.getId());
        request.setApproverName(current.getDisplayName());
    }
}
