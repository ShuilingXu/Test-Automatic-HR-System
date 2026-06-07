package com.autohr.modules.recruitment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("recruitment_job")
public class RecruitmentJob {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String jobCode;
    private String jobTitle;
    private String departmentName;
    private String workLocation;
    private String jobType;
    private Integer headcount;
    private String requirements;
    private String responsibilities;
    private String salaryRange;
    private LocalDate publishDate;
    private LocalDate closeDate;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
