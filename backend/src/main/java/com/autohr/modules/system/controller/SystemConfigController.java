package com.autohr.modules.system.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private static final String[] CONFIG_KEYS = {
            "ALIYUN_ACCESS_KEY_ID", "ALIYUN_ACCESS_KEY_SECRET", "ALIYUN_SMS_SIGN_NAME", "ALIYUN_SMS_TEMPLATE_CODE",
            "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM", "SMTP_SSL_ENABLED", "SMTP_STARTTLS_ENABLED",
            "ALIYUN_STT_APP_KEY", "ALIYUN_STT_ENDPOINT", "ALIYUN_OSS_BUCKET_NAME", "ALIYUN_OSS_ENDPOINT",
            "DB_TYPE", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET",
            "INTERVIEW_VIDEO_FFMPEG_PATH", "INTERVIEW_VIDEO_VIDEO_CODEC", "INTERVIEW_VIDEO_AUDIO_CODEC",
            "INTERVIEW_STUN_URLS", "INTERVIEW_TURN_URLS", "INTERVIEW_TURN_USERNAME", "INTERVIEW_TURN_CREDENTIAL",
            "TURN_HOST", "TURN_EXTERNAL_IP", "TURN_PRIVATE_IP", "TURN_REALM", "TURN_MIN_PORT", "TURN_MAX_PORT",
            "RESUME_OCR_ENABLED", "RESUME_OCR_TESSERACT_PATH", "RESUME_OCR_LANGUAGE", "RESUME_OCR_DPI", "RESUME_OCR_MAX_PAGES"
    };

    private final SystemConfigService systemConfigService;

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> getConfig() {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        mask(config, "ALIYUN_ACCESS_KEY_SECRET");
        mask(config, "JWT_SECRET");
        mask(config, "DB_PASSWORD");
        mask(config, "SMTP_PASSWORD");
        return ApiResponse.success(config);
    }

    @PostMapping("/config")
    public ApiResponse<Map<String, String>> saveConfig(@RequestBody Map<String, String> updates) {
        updates.entrySet().removeIf(e -> "****".equals(e.getValue()) || (e.getValue() != null && e.getValue().contains("****")));
        systemConfigService.saveConfig(updates);
        return ApiResponse.success("配置已保存，部分配置需要重启服务生效", systemConfigService.loadConfig(CONFIG_KEYS));
    }

    private void mask(Map<String, String> config, String key) {
        if (config.containsKey(key) && !config.get(key).isEmpty()) {
            config.put(key, "****");
        }
    }
}
