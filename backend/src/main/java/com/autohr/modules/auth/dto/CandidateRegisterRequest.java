package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CandidateRegisterRequest {
    @NotBlank(message = "用户名必填")
    private String username;
    @NotBlank(message = "密码必填")
    private String password;
    @NotBlank(message = "姓名必填")
    private String displayName;
    @NotBlank(message = "手机号必填")
    private String mobilePhone;
    private String email;
}
