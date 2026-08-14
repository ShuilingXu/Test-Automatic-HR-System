package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartInterviewProcessRequest {
    @NotNull(message = "招聘候选人必填")
    private Long recruitmentCandidateId;
    @NotNull(message = "面试者用户必填")
    private Long intervieweeUserId;
    @NotNull(message = "岗位必填")
    private Long jobId;
    private Long templateId;
    private Integer aiThresholdScore;
    private Integer aiFollowUpThreshold;
    private Integer aiMinQuestionRounds;
    private Integer aiMaxQuestionRounds;
    private Integer antiCheatSwitchLimit;
    private String aiOutputMode;
}
