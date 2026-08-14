package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AntiCheatEventRequest {
    @NotNull(message = "面试流程必填")
    private Long processId;
    @NotBlank(message = "事件类型必填")
    @Size(max = 64, message = "事件类型不能超过64个字符")
    private String eventType;
    @Size(max = 1000, message = "事件说明不能超过1000个字符")
    private String detail;
}
