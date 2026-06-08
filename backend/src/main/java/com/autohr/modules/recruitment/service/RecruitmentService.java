package com.autohr.modules.recruitment.service;

import com.autohr.modules.recruitment.dto.CandidateApplyRequest;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.dto.JobSaveRequest;
import com.autohr.modules.recruitment.dto.JobVO;
import com.autohr.modules.recruitment.dto.ResumeFileVO;
import com.autohr.modules.recruitment.entity.RecruitmentResumeFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RecruitmentService {

    JobVO saveJob(JobSaveRequest request);

    List<JobVO> listJobs(Integer status, String departmentName, String jobType, String keyword);

    JobVO getJob(Long id);

    void deleteJob(Long id);

    CandidateVO apply(CandidateApplyRequest request, String intervieweeUsername);

    List<CandidateVO> listCandidates(Long jobId, String status, String interviewStageStatus, String keyword);

    List<CandidateVO> listMyCandidates(String intervieweeUsername);

    CandidateVO getCandidate(Long id);

    CandidateVO rejectCandidateResume(Long id);

    void deleteCandidate(Long id);

    ResumeFileVO uploadResume(Long candidateId, String intervieweeUsername, MultipartFile file);

    RecruitmentResumeFile getResumeFile(Long id, String username, boolean privileged);
}
