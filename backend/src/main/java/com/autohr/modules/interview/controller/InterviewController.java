package com.autohr.modules.interview.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.interview.dto.AssignCandidateRequest;
import com.autohr.modules.interview.dto.BatchSaveRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.QuestionSaveRequest;
import com.autohr.modules.interview.dto.SubmissionSaveRequest;
import com.autohr.modules.interview.dto.SubmissionScoreRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/admin/batches")
    public ApiResponse<InterviewVO> saveBatch(@Valid @RequestBody BatchSaveRequest request) {
        return ApiResponse.success(interviewService.saveBatch(request));
    }

    @GetMapping("/admin/batches")
    public ApiResponse<List<InterviewVO>> listBatches(@RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.success(interviewService.listBatches(status, keyword));
    }

    @PostMapping("/admin/questions")
    public ApiResponse<InterviewVO> saveQuestion(@Valid @RequestBody QuestionSaveRequest request) {
        return ApiResponse.success(interviewService.saveQuestion(request));
    }

    @GetMapping("/admin/questions")
    public ApiResponse<List<InterviewVO>> listQuestions(@RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.success(interviewService.listQuestions(status, keyword));
    }

    @PostMapping("/admin/candidates")
    public ApiResponse<InterviewVO> assignCandidate(@Valid @RequestBody AssignCandidateRequest request) {
        return ApiResponse.success(interviewService.assignCandidate(request));
    }

    @GetMapping("/admin/candidates")
    public ApiResponse<List<InterviewVO>> listCandidates(@RequestParam(required = false) Long batchId,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String keyword) {
        return ApiResponse.success(interviewService.listCandidates(batchId, status, keyword));
    }

    @GetMapping("/admin/submissions")
    public ApiResponse<List<InterviewVO>> listSubmissions(@RequestParam(required = false) Long interviewCandidateId) {
        return ApiResponse.success(interviewService.listSubmissions(interviewCandidateId));
    }

    @PostMapping("/admin/submissions/{submissionId}/score")
    public ApiResponse<InterviewVO> scoreSubmission(@PathVariable Long submissionId,
                                                    @Valid @RequestBody SubmissionScoreRequest request) {
        return ApiResponse.success(interviewService.scoreSubmission(submissionId, request));
    }

    @GetMapping("/candidates/{interviewCandidateId}/questions")
    public ApiResponse<List<InterviewVO>> listCandidateQuestions(@PathVariable Long interviewCandidateId) {
        return ApiResponse.success(interviewService.listCandidateQuestions(interviewCandidateId));
    }

    @PostMapping("/submissions")
    public ApiResponse<InterviewVO> submitAnswer(@Valid @RequestBody SubmissionSaveRequest request) {
        return ApiResponse.success(interviewService.submitAnswer(request));
    }
}
