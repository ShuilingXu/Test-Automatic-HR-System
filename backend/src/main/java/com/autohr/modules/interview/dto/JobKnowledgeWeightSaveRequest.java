package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobKnowledgeWeightSaveRequest {
    private Long id;
    @NotNull(message = "岗位必填")
    private Long jobId;
    @NotNull(message = "知识库必填")
    private Long knowledgeBaseId;
    @NotNull(message = "权重必填")
    private Integer weight;
}
