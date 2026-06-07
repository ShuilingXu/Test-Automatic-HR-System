package com.autohr.modules.interview.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.interview.dto.AssignCandidateRequest;
import com.autohr.modules.interview.dto.BatchSaveRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.QuestionSaveRequest;
import com.autohr.modules.interview.dto.SubmissionSaveRequest;
import com.autohr.modules.interview.dto.SubmissionScoreRequest;
import com.autohr.modules.interview.entity.InterviewBatch;
import com.autohr.modules.interview.entity.InterviewCandidate;
import com.autohr.modules.interview.entity.InterviewQuestion;
import com.autohr.modules.interview.entity.InterviewSubmission;
import com.autohr.modules.interview.mapper.InterviewBatchMapper;
import com.autohr.modules.interview.mapper.InterviewCandidateMapper;
import com.autohr.modules.interview.mapper.InterviewQuestionMapper;
import com.autohr.modules.interview.mapper.InterviewSubmissionMapper;
import com.autohr.modules.interview.service.InterviewService;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.service.RecruitmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewBatchMapper batchMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewCandidateMapper candidateMapper;
    private final InterviewSubmissionMapper submissionMapper;
    private final RecruitmentService recruitmentService;

    @Override
    @Transactional
    public InterviewVO saveBatch(BatchSaveRequest request) {
        InterviewBatch batch = request.getId() == null ? new InterviewBatch() : requireBatch(request.getId());
        BeanUtils.copyProperties(request, batch);
        if (StrUtil.isBlank(batch.getBatchCode())) {
            batch.setBatchCode("IB-" + Math.abs(request.getBatchName().hashCode()));
        }
        batch.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            batch.setId(nextId(batchMapper.selectList(null).stream().map(InterviewBatch::getId).toList()));
            batchMapper.insert(batch);
        } else {
            batchMapper.updateById(batch);
        }
        return toBatchVO(requireBatch(batch.getId()));
    }

    @Override
    public List<InterviewVO> listBatches(Integer status, String keyword) {
        return batchMapper.selectList(new LambdaQueryWrapper<InterviewBatch>()
                .eq(status != null, InterviewBatch::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewBatch::getBatchName, keyword).or().like(InterviewBatch::getBatchCode, keyword))
                .orderByDesc(InterviewBatch::getId)).stream().map(this::toBatchVO).toList();
    }

    @Override
    @Transactional
    public InterviewVO saveQuestion(QuestionSaveRequest request) {
        InterviewQuestion question = request.getId() == null ? new InterviewQuestion() : requireQuestion(request.getId());
        BeanUtils.copyProperties(request, question);
        question.setQuestionType(StrUtil.blankToDefault(request.getQuestionType(), "TEXT"));
        question.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            question.setId(nextId(questionMapper.selectList(null).stream().map(InterviewQuestion::getId).toList()));
            questionMapper.insert(question);
        } else {
            questionMapper.updateById(question);
        }
        return toQuestionVO(requireQuestion(question.getId()));
    }

    @Override
    public List<InterviewVO> listQuestions(Integer status, String keyword) {
        return questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(status != null, InterviewQuestion::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewQuestion::getQuestionTitle, keyword).or().like(InterviewQuestion::getTags, keyword))
                .orderByDesc(InterviewQuestion::getId)).stream().map(this::toQuestionVO).toList();
    }

    @Override
    @Transactional
    public InterviewVO assignCandidate(AssignCandidateRequest request) {
        requireBatch(request.getBatchId());
        CandidateVO candidateVO = recruitmentService.getCandidate(request.getRecruitmentCandidateId());
        InterviewCandidate candidate = new InterviewCandidate();
        candidate.setId(nextId(candidateMapper.selectList(null).stream().map(InterviewCandidate::getId).toList()));
        candidate.setBatchId(request.getBatchId());
        candidate.setRecruitmentCandidateId(request.getRecruitmentCandidateId());
        candidate.setCandidateName(candidateVO.getFullName());
        candidate.setMobilePhone(candidateVO.getMobilePhone());
        candidate.setInterviewStatus("ASSIGNED");
        candidate.setTotalScore(0);
        candidateMapper.insert(candidate);
        return toCandidateVO(candidate);
    }

    @Override
    public List<InterviewVO> listCandidates(Long batchId, String status, String keyword) {
        return candidateMapper.selectList(new LambdaQueryWrapper<InterviewCandidate>()
                .eq(batchId != null, InterviewCandidate::getBatchId, batchId)
                .eq(StrUtil.isNotBlank(status), InterviewCandidate::getInterviewStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(InterviewCandidate::getCandidateName, keyword).or().like(InterviewCandidate::getMobilePhone, keyword))
                .orderByDesc(InterviewCandidate::getId)).stream().map(this::toCandidateVO).toList();
    }

    @Override
    public List<InterviewVO> listCandidateQuestions(Long interviewCandidateId) {
        requireCandidate(interviewCandidateId);
        return questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getStatus, 1).orderByAsc(InterviewQuestion::getId))
                .stream().map(this::toQuestionVO).toList();
    }

    @Override
    @Transactional
    public InterviewVO submitAnswer(SubmissionSaveRequest request) {
        InterviewCandidate candidate = requireCandidate(request.getInterviewCandidateId());
        requireQuestion(request.getQuestionId());
        InterviewSubmission submission = new InterviewSubmission();
        BeanUtils.copyProperties(request, submission);
        submission.setId(nextId(submissionMapper.selectList(null).stream().map(InterviewSubmission::getId).toList()));
        submissionMapper.insert(submission);
        candidate.setInterviewStatus("SUBMITTED");
        candidateMapper.updateById(candidate);
        return toSubmissionVO(submissionMapper.selectById(submission.getId()));
    }

    @Override
    @Transactional
    public InterviewVO scoreSubmission(Long submissionId, SubmissionScoreRequest request) {
        InterviewSubmission submission = requireSubmission(submissionId);
        submission.setScore(request.getScore());
        submission.setReviewerComment(request.getReviewerComment());
        submissionMapper.updateById(submission);
        refreshCandidateScore(submission.getInterviewCandidateId());
        return toSubmissionVO(submissionMapper.selectById(submissionId));
    }

    @Override
    public List<InterviewVO> listSubmissions(Long interviewCandidateId) {
        return submissionMapper.selectList(new LambdaQueryWrapper<InterviewSubmission>()
                .eq(interviewCandidateId != null, InterviewSubmission::getInterviewCandidateId, interviewCandidateId)
                .orderByDesc(InterviewSubmission::getId)).stream().map(this::toSubmissionVO).toList();
    }

    private void refreshCandidateScore(Long interviewCandidateId) {
        InterviewCandidate candidate = requireCandidate(interviewCandidateId);
        int total = submissionMapper.selectList(new LambdaQueryWrapper<InterviewSubmission>().eq(InterviewSubmission::getInterviewCandidateId, interviewCandidateId))
                .stream().map(InterviewSubmission::getScore).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        candidate.setTotalScore(total);
        candidate.setInterviewStatus("SCORED");
        candidateMapper.updateById(candidate);
    }

    private InterviewBatch requireBatch(Long id) {
        InterviewBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException("面试批次不存在: " + id);
        return batch;
    }

    private InterviewQuestion requireQuestion(Long id) {
        InterviewQuestion question = questionMapper.selectById(id);
        if (question == null) throw new BusinessException("面试题目不存在: " + id);
        return question;
    }

    private InterviewCandidate requireCandidate(Long id) {
        InterviewCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) throw new BusinessException("面试候选人不存在: " + id);
        return candidate;
    }

    private InterviewSubmission requireSubmission(Long id) {
        InterviewSubmission submission = submissionMapper.selectById(id);
        if (submission == null) throw new BusinessException("答题记录不存在: " + id);
        return submission;
    }

    private InterviewVO toBatchVO(InterviewBatch batch) {
        InterviewVO vo = new InterviewVO();
        BeanUtils.copyProperties(batch, vo);
        return vo;
    }

    private InterviewVO toQuestionVO(InterviewQuestion question) {
        InterviewVO vo = new InterviewVO();
        BeanUtils.copyProperties(question, vo);
        return vo;
    }

    private InterviewVO toCandidateVO(InterviewCandidate candidate) {
        InterviewVO vo = new InterviewVO();
        BeanUtils.copyProperties(candidate, vo);
        return vo;
    }

    private InterviewVO toSubmissionVO(InterviewSubmission submission) {
        InterviewVO vo = new InterviewVO();
        BeanUtils.copyProperties(submission, vo);
        vo.setId(submission.getId());
        return vo;
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
