package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VideoSignalRequest {
    @Size(max = 131072, message = "视频协商信息不能超过131072个字符")
    private String offerSdp;
    @Size(max = 131072, message = "视频协商信息不能超过131072个字符")
    private String answerSdp;
    @Size(max = 4096, message = "ICE候选不能超过4096个字符")
    private String iceCandidate;
    @Size(max = 500, message = "录像路径不能超过500个字符")
    private String recordingPath;
}
