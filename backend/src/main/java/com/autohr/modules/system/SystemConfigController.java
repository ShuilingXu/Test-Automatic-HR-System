package com.autohr.modules.system;

import com.autohr.common.api.ApiResponse;
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
            "ALIYUN_ACCESS_KEY_ID",
            "ALIYUN_ACCESS_KEY_SECRET",
            "ALIYUN_SMS_SIGN_NAME",
            "ALIYUN_SMS_TEMPLATE_CODE",
            "ALIYUN_STT_APP_KEY",
            "ALIYUN_STT_ENDPOINT",
            "ALIYUN_OSS_BUCKET_NAME",
            "ALIYUN_OSS_ENDPOINT",
            "DB_TYPE",
            "DB_URL",
            "DB_USERNAME",
            "DB_PASSWORD",
            "JWT_SECRET",
            "INTERVIEW_VIDEO_FFMPEG_PATH",
            "INTERVIEW_VIDEO_VIDEO_CODEC",
            "INTERVIEW_VIDEO_AUDIO_CODEC",
            "INTERVIEW_STUN_URLS",
            "INTERVIEW_TURN_URLS",
            "INTERVIEW_TURN_USERNAME",
            "INTERVIEW_TURN_CREDENTIAL",
            "TURN_HOST",
            "TURN_EXTERNAL_IP",
            "TURN_PRIVATE_IP",
            "TURN_REALM",
            "TURN_MIN_PORT",
            "TURN_MAX_PORT",
            "RESUME_OCR_ENABLED",
            "RESUME_OCR_TESSERACT_PATH",
            "RESUME_OCR_LANGUAGE",
            "RESUME_OCR_DPI",
            "RESUME_OCR_MAX_PAGES",
            "SMTP_HOST",
            "SMTP_PORT",
            "SMTP_USERNAME",
            "SMTP_PASSWORD",
            "SMTP_FROM",
            "SMTP_SSL"
    };

    private final SystemConfigService systemConfigService;

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> getConfig() {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        if (config.containsKey("ALIYUN_ACCESS_KEY_SECRET") && !config.get("ALIYUN_ACCESS_KEY_SECRET").isEmpty()) {
            String secret = config.get("ALIYUN_ACCESS_KEY_SECRET");
            config.put("ALIYUN_ACCESS_KEY_SECRET", secret.length() > 8 ? secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4) : "****");
        }
        if (config.containsKey("JWT_SECRET") && !config.get("JWT_SECRET").isEmpty()) {
            config.put("JWT_SECRET", "****");
        }
        if (config.containsKey("DB_PASSWORD") && !config.get("DB_PASSWORD").isEmpty()) {
            config.put("DB_PASSWORD", "****");
        }
        if (config.containsKey("SMTP_PASSWORD") && !config.get("SMTP_PASSWORD").isEmpty()) {
            config.put("SMTP_PASSWORD", "****");
        }
        return ApiResponse.success(config);
    }

    @PostMapping("/config")
    public ApiResponse<Map<String, String>> saveConfig(@RequestBody Map<String, String> updates) {
        updates.entrySet().removeIf(e -> e.getValue().equals("****"));
        systemConfigService.saveConfig(updates);
        return ApiResponse.success("配置已保存，部分配置需要重启服务生效", systemConfigService.loadConfig(CONFIG_KEYS));
    }
}
