package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AntiCheatEventRequest {
    @NotNull(message = "面试流程必填")
    private Long processId;
    @NotBlank(message = "事件类型必填")
    private String eventType;
    private String detail;
}
