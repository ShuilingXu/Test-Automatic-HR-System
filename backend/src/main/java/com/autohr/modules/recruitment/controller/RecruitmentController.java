package com.autohr.modules.recruitment.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.recruitment.dto.CandidateApplyRequest;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.dto.JobSaveRequest;
import com.autohr.modules.recruitment.dto.JobVO;
import com.autohr.modules.recruitment.dto.ResumeFileVO;
import com.autohr.modules.recruitment.entity.RecruitmentResumeFile;
import com.autohr.modules.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/recruitment")
@RequiredArgsConstructor
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @PostMapping("/admin/jobs")
    public ApiResponse<JobVO> saveJob(@Valid @RequestBody JobSaveRequest request) {
        return ApiResponse.success(recruitmentService.saveJob(request));
    }

    @GetMapping("/admin/jobs")
    public ApiResponse<List<JobVO>> listAdminJobs(@RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.success(recruitmentService.listJobs(status, keyword));
    }

    @GetMapping("/admin/jobs/{id}")
    public ApiResponse<JobVO> getAdminJob(@PathVariable Long id) {
        return ApiResponse.success(recruitmentService.getJob(id));
    }

    @DeleteMapping("/admin/jobs/{id}")
    public ApiResponse<Void> deleteJob(@PathVariable Long id) {
        recruitmentService.deleteJob(id);
        return ApiResponse.success("deleted", null);
    }

    @GetMapping("/admin/candidates")
    public ApiResponse<List<CandidateVO>> listCandidates(@RequestParam(required = false) Long jobId,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String keyword) {
        return ApiResponse.success(recruitmentService.listCandidates(jobId, status, keyword));
    }

    @GetMapping("/admin/candidates/{id}")
    public ApiResponse<CandidateVO> getCandidate(@PathVariable Long id) {
        return ApiResponse.success(recruitmentService.getCandidate(id));
    }

    @DeleteMapping("/admin/candidates/{id}")
    public ApiResponse<Void> deleteCandidate(@PathVariable Long id) {
        recruitmentService.deleteCandidate(id);
        return ApiResponse.success("deleted", null);
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobVO>> listOpenJobs(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(recruitmentService.listJobs(1, keyword));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<JobVO> getOpenJob(@PathVariable Long id) {
        return ApiResponse.success(recruitmentService.getJob(id));
    }

    @PostMapping("/candidates")
    public ApiResponse<CandidateVO> apply(Authentication authentication,
                                          @Valid @RequestBody CandidateApplyRequest request) {
        return ApiResponse.success(recruitmentService.apply(request, authentication.getName()));
    }

    @PostMapping("/candidates/{candidateId}/resume")
    public ApiResponse<ResumeFileVO> uploadResume(@PathVariable Long candidateId,
                                                  @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(recruitmentService.uploadResume(candidateId, file));
    }

    @GetMapping("/resumes/{id}")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long id) {
        RecruitmentResumeFile resumeFile = recruitmentService.getResumeFile(id);
        Path path = Paths.get(resumeFile.getFilePath()).toAbsolutePath().normalize();
        Resource resource = new FileSystemResource(path);
        String fileName = URLEncoder.encode(resumeFile.getOriginalFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentType = resumeFile.getContentType() == null ? "application/octet-stream" : resumeFile.getContentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + fileName)
                .body(resource);
    }
}
