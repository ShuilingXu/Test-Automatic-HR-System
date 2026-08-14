package com.autohr.modules.interview.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.interview.entity.InterviewVideoSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoMergeService {

    private final S3ObjectStorageService s3ObjectStorageService;

    @Value("${interview.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${interview.video.video-codec:libvpx-vp9}")
    private String videoCodec;

    @Value("${interview.video.audio-codec:libopus}")
    private String audioCodec;

    public boolean canMerge(InterviewVideoSession session) {
        return isReadableRecording(session.getHrRecordingPath()) && isReadableRecording(session.getIntervieweeRecordingPath());
    }

    public void mergeRecordings(InterviewVideoSession session) {
        if (!canMerge(session)) {
            return;
        }
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            Path output = UploadPaths.RECORDING_DIR.resolve(session.getVideoSerialNo() + "-merged.webm").normalize().toAbsolutePath();
            if (!output.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("拼接视频路径非法");
            }
            mergeSideBySide(session, output);
            s3ObjectStorageService.archiveIfEnabled(output, "interview-recordings/" + output.getFileName(), "video/webm");
            session.setMergedRecordingPath(output.toString());
            session.setMergedRecordingFileName(output.getFileName().toString());
            session.setRecordingPath(output.toString());
            session.setRecordingFileName(output.getFileName().toString());
        } catch (IOException ex) {
            throw new BusinessException("拼接视频失败: " + ex.getMessage());
        }
    }

    public void extractAudio(InterviewVideoSession session) {
        String source = session.getMergedRecordingPath() == null ? session.getRecordingPath() : session.getMergedRecordingPath();
        if (source == null || !Files.isRegularFile(Path.of(source))) {
            throw new BusinessException("视频文件不存在，不能分离音频");
        }
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            Path output = UploadPaths.RECORDING_DIR.resolve(session.getVideoSerialNo() + "-audio.pcm").normalize().toAbsolutePath();
            if (!output.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("音频文件路径非法");
            }
            if (canMerge(session)) {
                runFfmpeg(List.of(
                        ffmpegPath,
                        "-y",
                        "-i", session.getHrRecordingPath(),
                        "-i", session.getIntervieweeRecordingPath(),
                        "-filter_complex", "[0:a:0][1:a:0]amix=inputs=2:duration=longest:dropout_transition=0,aresample=16000[a]",
                        "-map", "[a]",
                        "-ac", "1",
                        "-ar", "16000",
                        "-f", "s16le",
                        "-c:a", "pcm_s16le",
                        output.toString()
                ), "混合双方音频失败");
            } else {
                runFfmpeg(List.of(
                        ffmpegPath,
                        "-y",
                        "-i", source,
                        "-map", "0:a:0",
                        "-vn",
                        "-ac", "1",
                        "-ar", "16000",
                        "-f", "s16le",
                        "-c:a", "pcm_s16le",
                        output.toString()
                ), "分离音频失败");
            }
            s3ObjectStorageService.archiveIfEnabled(output, "interview-recordings/" + output.getFileName(), "audio/L16");
            session.setAudioPath(output.toString());
            session.setAudioFileName(output.getFileName().toString());
        } catch (IOException ex) {
            throw new BusinessException("分离音频失败: " + ex.getMessage());
        }
    }

    public String extractSpeakerAudio(InterviewVideoSession session, String speaker) {
        String source = "hr".equals(speaker) ? session.getHrRecordingPath() : session.getIntervieweeRecordingPath();
        if (source == null || !Files.isRegularFile(Path.of(source))) {
            throw new BusinessException("" + speaker + "录制文件不存在，不能分离音频");
        }
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            Path output = UploadPaths.RECORDING_DIR.resolve(session.getVideoSerialNo() + "-" + speaker + "-audio.pcm").normalize().toAbsolutePath();
            if (!output.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("说话人音频文件路径非法");
            }
            runFfmpeg(List.of(
                    ffmpegPath,
                    "-y",
                    "-i", source,
                    "-map", "0:a:0",
                    "-vn",
                    "-ac", "1",
                    "-ar", "16000",
                    "-f", "s16le",
                    "-c:a", "pcm_s16le",
                    output.toString()
            ), "分离" + speaker + "音频失败");
            s3ObjectStorageService.archiveIfEnabled(output, "interview-recordings/" + output.getFileName(), "audio/L16");
            return output.toString();
        } catch (IOException ex) {
            throw new BusinessException("分离" + speaker + "音频失败: " + ex.getMessage());
        }
    }

    private void mergeSideBySide(InterviewVideoSession session, Path output) {
        runFfmpeg(List.of(
                ffmpegPath,
                "-y",
                "-i", session.getHrRecordingPath(),
                "-i", session.getIntervieweeRecordingPath(),
                "-filter_complex", "[0:v]scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2,setsar=1[v0];[1:v]scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2,setsar=1[v1];[v0][v1]hstack=inputs=2[v];[0:a:0][1:a:0]amix=inputs=2:duration=longest:dropout_transition=0[a]",
                "-map", "[v]",
                "-map", "[a]",
                "-c:v", videoCodec,
                "-b:v", "1600k",
                "-c:a", audioCodec,
                "-shortest",
                output.toString()
        ), "横向拼接视频失败");
    }

    private void runFfmpeg(List<String> command, String errorMessage) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean exited = process.waitFor(Duration.ofMinutes(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                throw new BusinessException(errorMessage + ": ffmpeg执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(errorMessage + ": " + abbreviate(output));
            }
        } catch (IOException ex) {
            throw new BusinessException(errorMessage + ": 未找到ffmpeg，请安装ffmpeg或配置INTERVIEW_VIDEO_FFMPEG_PATH/FFMPEG_PATH");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(errorMessage + ": ffmpeg执行被中断");
        }
    }

    private boolean isReadableRecording(String path) {
        return path != null && Files.isRegularFile(Path.of(path)) && Files.isReadable(Path.of(path));
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "空输出";
        }
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }
}
