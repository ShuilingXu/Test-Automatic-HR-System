package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmConfigSaveRequest {
    private Long id;
    @NotBlank(message = "配置名称必填")
    private String configName;
    @NotBlank(message = "模型角色必填")
    private String modelRole;
    @NotBlank(message = "OpenAI接口地址必填")
    private String baseUrl;
    private String apiKeyMasked;
    @NotBlank(message = "模型名称必填")
    private String modelName;
    private String promptTemplate;
    private Integer status;
}
