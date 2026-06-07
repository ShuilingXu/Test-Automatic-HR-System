package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_process")
public class InterviewProcess {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long recruitmentCandidateId;
    private Long intervieweeUserId;
    private Long jobId;
    private String currentStage;
    private String stageStatus;
    private String overallStatus;
    private Integer aiThresholdScore;
    private Integer aiAverageScore;
    private Integer videoApproved;
    private Integer onsiteApproved;
    private Long approvedHrUserId;
    private String approvedHrName;
    private String processStatusView;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
