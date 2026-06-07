package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InterviewDecisionRequest {
    @NotNull(message = "通过标记必填")
    private Integer approved;
    private String approverName;
    private Long approverUserId;
    private String comment;
}
