package com.autohr.modules.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class JobSaveRequest {

    private Long id;
    private String jobCode;

    @NotBlank(message = "招聘岗位名称必填")
    private String jobTitle;

    @NotBlank(message = "招聘部门必填")
    private String departmentName;

    private String workLocation;
    private String jobType;

    @NotNull(message = "招聘人数必填")
    private Integer headcount;

    @NotBlank(message = "任职要求必填")
    private String requirements;

    @NotBlank(message = "岗位职责必填")
    private String responsibilities;

    private String salaryRange;
    private LocalDate publishDate;
    private LocalDate closeDate;
    private Integer status;
}
