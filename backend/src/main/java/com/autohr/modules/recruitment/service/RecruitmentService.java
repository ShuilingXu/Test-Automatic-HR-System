package com.autohr.modules.recruitment.service;

import com.autohr.modules.recruitment.dto.CandidateApplyRequest;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.dto.JobSaveRequest;
import com.autohr.modules.recruitment.dto.JobVO;
import com.autohr.modules.recruitment.dto.ResumeFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RecruitmentService {

    JobVO saveJob(JobSaveRequest request);

    List<JobVO> listJobs(Integer status, String keyword);

    JobVO getJob(Long id);

    void deleteJob(Long id);

    CandidateVO apply(CandidateApplyRequest request);

    List<CandidateVO> listCandidates(Long jobId, String status, String keyword);

    CandidateVO getCandidate(Long id);

    ResumeFileVO uploadResume(Long candidateId, MultipartFile file);
}
