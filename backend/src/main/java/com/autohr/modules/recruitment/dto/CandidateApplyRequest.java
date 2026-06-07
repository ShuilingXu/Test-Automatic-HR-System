package com.autohr.modules.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CandidateApplyRequest {

    @NotNull(message = "招聘岗位必填")
    private Long jobId;

    @NotBlank(message = "姓名必填")
    private String fullName;

    @NotBlank(message = "手机号必填")
    private String mobilePhone;

    private String email;
    private String idCardNo;

    @NotBlank(message = "专业必填")
    private String major;

    private String educationLevel;
    private String graduationSchool;
    private Integer yearsOfExperience;
    private String expectedSalary;
    private String selfIntroduction;
}
