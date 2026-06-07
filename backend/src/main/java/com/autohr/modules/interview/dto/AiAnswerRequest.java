package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiAnswerRequest {
    @NotNull(message = "流程ID必填")
    private Long processId;
    @NotBlank(message = "回答内容必填")
    private String answerContent;
}
