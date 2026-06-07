package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeSaveRequest {

    private Long id;
    private String employeeCode;

    @NotBlank(message = "姓名必填")
    private String fullName;

    @NotBlank(message = "身份证号必填")
    private String idCardNo;

    @NotBlank(message = "手机号必填")
    private String mobilePhone;

    private String email;

    @NotBlank(message = "招聘专业必填")
    private String recruitmentMajor;

    @NotBlank(message = "岗位必填")
    private String positionName;

    private Long managerEmployeeId;

    @NotNull(message = "直属部门必填")
    private Long departmentId;

    @NotBlank(message = "银行卡号必填")
    private String bankAccountNo;

    @NotBlank(message = "开户银行必填")
    private String bankName;

    private LocalDate hireDate;
    private Integer employmentStatus;
    private String sourceChannel;
    private String notes;
}
