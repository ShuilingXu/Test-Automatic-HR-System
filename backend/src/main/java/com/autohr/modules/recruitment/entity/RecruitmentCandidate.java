package com.autohr.modules.recruitment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recruitment_candidate")
public class RecruitmentCandidate {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long jobId;
    private String fullName;
    private String mobilePhone;
    private String email;
    private String idCardNo;
    private String major;
    private String educationLevel;
    private String graduationSchool;
    private Integer yearsOfExperience;
    private String expectedSalary;
    private String selfIntroduction;
    private String applicationStatus;
    private Long resumeFileId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
