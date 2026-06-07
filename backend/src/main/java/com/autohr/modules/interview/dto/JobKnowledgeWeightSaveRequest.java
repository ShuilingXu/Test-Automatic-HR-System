package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobKnowledgeWeightSaveRequest {
    private Long id;
    @NotNull(message = "岗位必填")
    private Long jobId;
    @NotNull(message = "知识库必填")
    private Long knowledgeBaseId;
    @NotBlank(message = "知识点必填")
    private String knowledgePoint;
    @NotNull(message = "权重必填")
    private Integer weight;
}
