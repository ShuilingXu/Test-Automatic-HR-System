package com.autohr.modules.auth.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private SessionUserVO user;
}
