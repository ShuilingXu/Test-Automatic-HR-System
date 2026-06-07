package com.autohr.modules.auth.dto;

import lombok.Data;

@Data
public class UserAdminUpdateRequest {
    private String roleCode;
    private Integer status;
    private String displayName;
    private String mobilePhone;
    private String email;
}
