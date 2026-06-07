package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeBaseSaveRequest {
    private Long id;
    @NotBlank(message = "知识库名称必填")
    private String knowledgeBaseName;
    private String techCategory;
    private String jobCategory;
    private Integer status;
}
