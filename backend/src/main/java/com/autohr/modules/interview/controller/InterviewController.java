package com.autohr.modules.interview.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.DownloadUrlResponse;
import com.autohr.common.file.FileDownloadSupport;
import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.AntiCheatEventRequest;
import com.autohr.modules.interview.dto.InterviewDecisionRequest;
import com.autohr.modules.interview.dto.InterviewProcessTemplateSaveRequest;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final AuthService authService;
    private final S3ObjectStorageService s3ObjectStorageService;

    @Value("${interview.webrtc.stun-urls:stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478}")
    private String stunUrls;

    @Value("${interview.webrtc.turn-urls:}")
    private String turnUrls;

    @Value("${interview.webrtc.turn-shared-secret:}")
    private String turnSharedSecret;

    @Value("${interview.webrtc.turn-credential-ttl-seconds:3600}")
    private long turnCredentialTtlSeconds;

    @Value("${interview.security.disable-devtools-shortcuts:true}")
    private boolean disableDevtoolsShortcuts;

    @GetMapping("/runtime-config")
    public ApiResponse<Map<String, Boolean>> getRuntimeConfig() {
        return ApiResponse.success(Map.of("disableDevtoolsShortcuts", disableDevtoolsShortcuts));
    }

    @GetMapping("/ice-servers")
    public ApiResponse<List<IceServerVO>> getIceServers(Authentication authentication) {
        List<IceServerVO> servers = new java.util.ArrayList<>();
        List<String> stun = splitUrls(stunUrls);
        if (!stun.isEmpty()) {
            servers.add(new IceServerVO(stun, null, null));
        }
        List<String> turn = splitUrls(turnUrls);
        if (!turn.isEmpty()) {
            if (turnSharedSecret == null || turnSharedSecret.isBlank()) {
                throw new BusinessException("TURN 地址已配置，但缺少 INTERVIEW_TURN_SHARED_SECRET");
            }
            SessionUserVO current = currentUser(authentication);
            long ttlSeconds = Math.max(60L, Math.min(turnCredentialTtlSeconds, 86400L));
            String username = Instant.now().plusSeconds(ttlSeconds).getEpochSecond() + ":" + current.getId();
            servers.add(new IceServerVO(turn, username, createTurnCredential(username)));
        }
        return ApiResponse.success(servers);
    }

    @PostMapping("/hr/knowledge-bases")
    public ApiResponse<InterviewVO> saveKnowledgeBase(@Valid @RequestBody KnowledgeBaseSaveRequest request) {
        return ApiResponse.success(interviewService.saveKnowledgeBase(request));
    }

    @GetMapping("/hr/knowledge-bases")
    public ApiResponse<PageResponse<InterviewVO>> listKnowledgeBases(@RequestParam(required = false) Integer status,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listKnowledgeBases(status, keyword),
                PageQuery.of(page, pageSize)));
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
    public ApiResponse<PageResponse<InterviewVO>> listKnowledgeItems(@RequestParam(required = false) Long knowledgeBaseId,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listKnowledgeItems(knowledgeBaseId, keyword),
                PageQuery.of(page, pageSize)));
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
    public ApiResponse<PageResponse<InterviewVO>> listJobKnowledgeWeights(@RequestParam(required = false) Long jobId,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listJobKnowledgeWeights(jobId),
                PageQuery.of(page, pageSize)));
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
    public ApiResponse<PageResponse<InterviewVO>> listLlmConfigs(@RequestParam(required = false) String modelRole,
                                                                   @RequestParam(required = false) Integer status,
                                                                   @RequestParam(required = false) Integer page,
                                                                   @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listLlmConfigs(modelRole, status),
                PageQuery.of(page, pageSize)));
    }

    @PostMapping("/it/llm-configs/{id}/delete")
    public ApiResponse<Void> deleteLlmConfig(@PathVariable Long id) {
        interviewService.deleteLlmConfig(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/hr/process-templates")
    public ApiResponse<InterviewVO> saveProcessTemplate(@Valid @RequestBody InterviewProcessTemplateSaveRequest request) {
        return ApiResponse.success(interviewService.saveProcessTemplate(request));
    }

    @GetMapping("/hr/process-templates")
    public ApiResponse<PageResponse<InterviewVO>> listProcessTemplates(@RequestParam(required = false) Integer status,
                                                                         @RequestParam(required = false) String keyword,
                                                                         @RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listProcessTemplates(status, keyword),
                PageQuery.of(page, pageSize)));
    }

    @GetMapping("/hr/process-templates/{id}")
    public ApiResponse<InterviewVO> getProcessTemplate(@PathVariable Long id) {
        return ApiResponse.success(interviewService.getProcessTemplate(id));
    }

    @PostMapping("/hr/process-templates/{id}/delete")
    public ApiResponse<Void> deleteProcessTemplate(@PathVariable Long id, @RequestParam Integer version) {
        interviewService.deleteProcessTemplate(id, version);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/hr/processes")
    public ApiResponse<InterviewVO> startProcess(@Valid @RequestBody StartInterviewProcessRequest request) {
        return ApiResponse.success(interviewService.startInterviewProcess(request));
    }

    @GetMapping("/hr/processes")
    public ApiResponse<PageResponse<InterviewVO>> listProcesses(@RequestParam(required = false) String overallStatus,
                                                                  @RequestParam(required = false) String stageStatus,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listProcesses(overallStatus, stageStatus, keyword),
                PageQuery.of(page, pageSize)));
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

    @PostMapping("/interviewee/ai-answer/stream")
    public SseEmitter submitAiAnswerStream(Authentication authentication,
                                           @Valid @RequestBody AiAnswerRequest request) {
        SessionUserVO current = currentUser(authentication);
        return interviewService.submitIntervieweeAiAnswerStream(request, current.getId());
    }

    @PostMapping("/interviewee/anti-cheat-event")
    public ApiResponse<InterviewVO> reportAntiCheatEvent(Authentication authentication,
                                                         @Valid @RequestBody AntiCheatEventRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.reportAntiCheatEvent(request, current.getId(), current.getDisplayName()));
    }

    @GetMapping("/hr/ai-records")
    public ApiResponse<PageResponse<InterviewVO>> listAiRecords(@RequestParam Long processId,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(PageResponse.slice(interviewService.listAiRecords(processId),
                PageQuery.of(page, pageSize)));
    }

    @GetMapping("/interviewee/ai-records")
    public ApiResponse<PageResponse<InterviewVO>> listIntervieweeAiRecords(Authentication authentication,
                                                                             @RequestParam Long processId,
                                                                             @RequestParam(required = false) Integer page,
                                                                             @RequestParam(required = false) Integer pageSize) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(PageResponse.slice(interviewService.listIntervieweeAiRecords(processId, current.getId()),
                PageQuery.of(page, pageSize)));
    }

    @PostMapping("/hr/video-session/{processId}")
    public ApiResponse<InterviewVO> createVideoSession(Authentication authentication,
                                                       @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.createVideoSession(processId, current.getId(), current.getDisplayName()));
    }

    @PostMapping("/interviewee/video-join/{processId}")
    public ApiResponse<InterviewVO> intervieweeJoin(Authentication authentication,
                                                    @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.intervieweeJoinVideo(processId, current.getId(), current.getDisplayName()));
    }

    @PostMapping("/hr/video-join/{processId}")
    public ApiResponse<InterviewVO> hrJoin(Authentication authentication,
                                           @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.hrJoinVideo(processId, current.getId(), current.getDisplayName()));
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
                                                   @Valid @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.publishHrOffer(processId, request));
    }

    @GetMapping("/hr/video-state/{processId}")
    public ApiResponse<VideoSignalVO> getHrVideoState(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.getVideoSignalState(processId));
    }

    @PostMapping("/interviewee/video-answer/{processId}")
    public ApiResponse<VideoSignalVO> submitAnswer(Authentication authentication,
                                                   @PathVariable Long processId,
                                                   @Valid @RequestBody VideoSignalRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.submitIntervieweeAnswer(processId, request, current.getId(), current.getDisplayName()));
    }

    @PostMapping("/hr/video-ice/{processId}")
    public ApiResponse<VideoSignalVO> addHrIce(@PathVariable Long processId,
                                               @Valid @RequestBody VideoSignalRequest request) {
        return ApiResponse.success(interviewService.addHrIceCandidate(processId, request));
    }

    @PostMapping("/interviewee/video-ice/{processId}")
    public ApiResponse<VideoSignalVO> addIntervieweeIce(Authentication authentication,
                                                        @PathVariable Long processId,
                                                        @Valid @RequestBody VideoSignalRequest request) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.addIntervieweeIceCandidate(processId, request, current.getId(), current.getDisplayName()));
    }

    @GetMapping("/interviewee/video-state/{processId}")
    public ApiResponse<VideoSignalVO> getVideoState(Authentication authentication,
                                                     @PathVariable Long processId) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.getIntervieweeVideoSignalState(processId, current.getId()));
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

    @PostMapping("/interviewee/ai-recording/{processId}")
    public ApiResponse<InterviewVO> uploadAiRecording(Authentication authentication,
                                                      @PathVariable Long processId,
                                                      @RequestParam String originalFileName,
                                                      @RequestParam(required = false) String contentType,
                                                      @RequestParam("file") MultipartFile file) {
        SessionUserVO current = currentUser(authentication);
        return ApiResponse.success(interviewService.uploadAiExamRecording(processId, current.getId(), current.getDisplayName(), originalFileName, contentType, file));
    }

    @GetMapping("/hr/video-recording/{processId}")
    public ResponseEntity<Resource> downloadRecording(@PathVariable Long processId,
                                                       @RequestParam(required = false) Long processStageId) {
        var session = interviewService.getDownloadableVideoSession(processId, processStageId);
        String path = session.getMergedRecordingPath() == null ? session.getRecordingPath() : session.getMergedRecordingPath();
        String fileName = session.getMergedRecordingFileName() == null ? session.getRecordingFileName() : session.getMergedRecordingFileName();
        return FileDownloadSupport.buildInlineResponse(
                path, UploadPaths.RECORDING_DIR, fileName, "video/webm", "录制文件不可访问");
    }

    @GetMapping("/hr/video-recording/{processId}/download-url")
    public ApiResponse<DownloadUrlResponse> getRecordingDownloadUrl(@PathVariable Long processId,
                                                                      @RequestParam(required = false) Long processStageId) {
        var session = interviewService.getDownloadableVideoSession(processId, processStageId);
        String fileName = session.getMergedRecordingFileName() == null ? session.getRecordingFileName() : session.getMergedRecordingFileName();
        return externalDownloadUrl("interview-recordings/" + fileName);
    }

    @PostMapping("/hr/video-summary/{processId}/retry")
    public ApiResponse<InterviewVO> retryVideoSummary(@PathVariable Long processId) {
        return ApiResponse.success(interviewService.retryVideoSummary(processId));
    }

    @GetMapping("/hr/ai-recording/{processId}")
    public ResponseEntity<Resource> downloadAiRecording(@PathVariable Long processId,
                                                         @RequestParam(required = false) Long processStageId) {
        var process = processStageId == null ? interviewService.getProcess(processId) : interviewService.getProcessStage(processId, processStageId);
        return FileDownloadSupport.buildInlineResponse(
                process.getAiRecordingPath(), UploadPaths.RECORDING_DIR, process.getAiRecordingFileName(),
                "video/webm", "AI问答视频不可访问");
    }

    @GetMapping("/hr/ai-recording/{processId}/download-url")
    public ApiResponse<DownloadUrlResponse> getAiRecordingDownloadUrl(@PathVariable Long processId,
                                                                        @RequestParam(required = false) Long processStageId) {
        var process = processStageId == null ? interviewService.getProcess(processId) : interviewService.getProcessStage(processId, processStageId);
        return externalDownloadUrl("interview-recordings/" + process.getAiRecordingFileName());
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

    private ApiResponse<DownloadUrlResponse> externalDownloadUrl(String objectName) {
        return ApiResponse.success(s3ObjectStorageService.presignExternalDownloadIfAvailable(objectName)
                .map(url -> new DownloadUrlResponse(url.toString()))
                .orElseGet(DownloadUrlResponse::localFallback));
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

    private String createTurnCredential(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(turnSharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成 TURN 临时凭据", ex);
        }
    }

    private void fillApprover(Authentication authentication, InterviewDecisionRequest request) {
        SessionUserVO current = currentUser(authentication);
        request.setApproverUserId(current.getId());
        request.setApproverName(current.getDisplayName());
    }
}
