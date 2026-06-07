package com.autohr.modules.hr.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeVO {

    private Long id;
    private String employeeCode;
    private String fullName;
    private String idCardNo;
    private String mobilePhone;
    private String email;
    private String recruitmentMajor;
    private String positionName;
    private Long managerEmployeeId;
    private String managerEmployeeName;
    private Long departmentId;
    private String departmentName;
    private String bankAccountNo;
    private String bankName;
    private LocalDate hireDate;
    private Integer employmentStatus;
    private String sourceChannel;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
