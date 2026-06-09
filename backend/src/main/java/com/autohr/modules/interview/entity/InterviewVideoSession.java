package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_video_session")
public class InterviewVideoSession {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long processId;
    private String videoSerialNo;
    private String videoJoinLink;
    private Long approverUserId;
    private String approverName;
    private LocalDateTime intervieweeJoinTime;
    private LocalDateTime hrJoinTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String recordingPath;
    private String hrRecordingPath;
    private String hrRecordingFileName;
    private String intervieweeRecordingPath;
    private String intervieweeRecordingFileName;
    private String mergedRecordingPath;
    private String mergedRecordingFileName;
    private String hrOfferSdp;
    private String intervieweeAnswerSdp;
    private String hrIceCandidates;
    private String intervieweeIceCandidates;
    private String recordingFileName;
    private String sessionStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
