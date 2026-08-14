package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InterviewDecisionRequest {
    @NotNull(message = "通过标记必填")
    @Min(value = 0, message = "审批结果必须为0或1")
    @Max(value = 1, message = "审批结果必须为0或1")
    private Integer approved;
    private String approverName;
    private Long approverUserId;
    private String comment;
    private Long departmentId;
}
