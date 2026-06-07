package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeItemSaveRequest {
    private Long id;
    @NotNull(message = "知识库必填")
    private Long knowledgeBaseId;
    @NotBlank(message = "知识点必填")
    private String knowledgePoint;
    @NotBlank(message = "知识内容必填")
    private String knowledgeContent;
    private Integer status;
}
