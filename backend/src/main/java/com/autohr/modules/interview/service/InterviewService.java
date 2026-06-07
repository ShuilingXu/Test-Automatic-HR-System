package com.autohr.modules.interview.service;

import com.autohr.modules.interview.dto.AssignCandidateRequest;
import com.autohr.modules.interview.dto.BatchSaveRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.QuestionSaveRequest;
import com.autohr.modules.interview.dto.SubmissionSaveRequest;
import com.autohr.modules.interview.dto.SubmissionScoreRequest;

import java.util.List;

public interface InterviewService {
    InterviewVO saveBatch(BatchSaveRequest request);
    List<InterviewVO> listBatches(Integer status, String keyword);
    InterviewVO saveQuestion(QuestionSaveRequest request);
    List<InterviewVO> listQuestions(Integer status, String keyword);
    InterviewVO assignCandidate(AssignCandidateRequest request);
    List<InterviewVO> listCandidates(Long batchId, String status, String keyword);
    List<InterviewVO> listCandidateQuestions(Long interviewCandidateId);
    InterviewVO submitAnswer(SubmissionSaveRequest request);
    InterviewVO scoreSubmission(Long submissionId, SubmissionScoreRequest request);
    List<InterviewVO> listSubmissions(Long interviewCandidateId);
}
