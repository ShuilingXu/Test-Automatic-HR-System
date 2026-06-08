package com.autohr.modules.interview.dto;

import lombok.Data;

@Data
public class VideoSignalRequest {
    private String offerSdp;
    private String answerSdp;
    private String iceCandidate;
    private String recordingPath;
}
