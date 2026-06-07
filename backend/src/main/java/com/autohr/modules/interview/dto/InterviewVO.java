package com.autohr.modules.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewVO {
    private Long id;
    private String batchCode;
    private String batchName;
    private Long jobId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private Integer status;
    private String questionTitle;
    private String questionType;
    private String difficulty;
    private String tags;
    private String content;
    private String referenceAnswer;
    private Integer score;
    private Long batchId;
    private Long recruitmentCandidateId;
    private String candidateName;
    private String mobilePhone;
    private String interviewStatus;
    private Integer totalScore;
    private String interviewerComment;
    private Long interviewCandidateId;
    private Long questionId;
    private String answerContent;
    private String reviewerComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
