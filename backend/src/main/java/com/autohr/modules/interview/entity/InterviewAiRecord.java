package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_ai_record")
public class InterviewAiRecord {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long processId;
    private Long knowledgeBaseId;
    private String knowledgePoint;
    private String questionContent;
    private String answerContent;
    private Integer interviewerScore;
    private Integer scorerScore;
    private Integer averageScore;
    private Integer sequenceNo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
