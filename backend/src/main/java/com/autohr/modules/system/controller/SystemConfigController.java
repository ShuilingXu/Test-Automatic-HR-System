package com.autohr.modules.system.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.net.URI;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private static final String[] CONFIG_KEYS = {
            "ALIYUN_SMS_ACCESS_KEY_ID", "ALIYUN_SMS_ACCESS_KEY_SECRET", "ALIYUN_SMS_ENDPOINT", "ALIYUN_SMS_SIGN_NAME", "ALIYUN_SMS_TEMPLATE_CODE",
            "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM", "SMTP_SSL_ENABLED", "SMTP_STARTTLS_ENABLED",
            "ALIYUN_STT_ACCESS_KEY_ID", "ALIYUN_STT_ACCESS_KEY_SECRET", "ALIYUN_STT_APP_KEY", "ALIYUN_STT_ENDPOINT",
            "S3_ENABLED", "S3_ENDPOINT", "S3_INTERNAL_ENDPOINT_ENABLED", "S3_INTERNAL_ENDPOINT", "S3_REGION", "S3_BUCKET", "S3_ACCESS_KEY_ID", "S3_SECRET_ACCESS_KEY", "S3_SESSION_TOKEN", "S3_PREFIX", "S3_PATH_STYLE_ACCESS",
            "DB_TYPE", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET",
            "INTERVIEW_VIDEO_FFMPEG_PATH", "INTERVIEW_VIDEO_VIDEO_CODEC", "INTERVIEW_VIDEO_AUDIO_CODEC",
            "INTERVIEW_STUN_URLS", "INTERVIEW_TURN_URLS", "INTERVIEW_TURN_SHARED_SECRET", "INTERVIEW_TURN_CREDENTIAL_TTL_SECONDS",
            "TURN_HOST", "TURN_EXTERNAL_IP", "TURN_PRIVATE_IP", "TURN_REALM", "TURN_MIN_PORT", "TURN_MAX_PORT",
            "RESUME_OCR_ENABLED", "RESUME_OCR_TESSERACT_PATH", "RESUME_OCR_LANGUAGE", "RESUME_OCR_DPI", "RESUME_OCR_MAX_PAGES"
    };
    private static final Set<String> CONFIG_KEY_SET = Set.of(CONFIG_KEYS);
    private static final Set<String> RETAIN_ON_BLANK_KEYS = Set.of(
            "ALIYUN_SMS_ACCESS_KEY_SECRET", "SMTP_PASSWORD", "S3_SECRET_ACCESS_KEY",
            "ALIYUN_STT_ACCESS_KEY_SECRET", "DB_PASSWORD", "JWT_SECRET", "INTERVIEW_TURN_SHARED_SECRET"
    );

    private final SystemConfigService systemConfigService;

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> getConfig() {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        maskSecrets(config);
        return ApiResponse.success(config);
    }

    private void maskSecrets(Map<String, String> config) {
        mask(config, "ALIYUN_SMS_ACCESS_KEY_SECRET");
        mask(config, "ALIYUN_STT_ACCESS_KEY_SECRET");
        mask(config, "S3_SECRET_ACCESS_KEY");
        mask(config, "S3_SESSION_TOKEN");
        mask(config, "JWT_SECRET");
        mask(config, "DB_PASSWORD");
        mask(config, "SMTP_PASSWORD");
        mask(config, "INTERVIEW_TURN_SHARED_SECRET");
    }

    @PostMapping("/config")
    public ApiResponse<Map<String, String>> saveConfig(@RequestBody Map<String, String> updates) {
        Map<String, String> safeUpdates = new LinkedHashMap<>(updates);
        safeUpdates.entrySet().removeIf(entry -> !CONFIG_KEY_SET.contains(entry.getKey()));
        safeUpdates.entrySet().removeIf(e -> "****".equals(e.getValue()));
        safeUpdates.entrySet().removeIf(e -> RETAIN_ON_BLANK_KEYS.contains(e.getKey()) && (e.getValue() == null || e.getValue().isBlank()));
        Map<String, String> effectiveConfig = systemConfigService.loadConfig(CONFIG_KEYS);
        effectiveConfig.putAll(safeUpdates);
        validateS3Config(effectiveConfig);
        systemConfigService.saveConfig(safeUpdates);
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        maskSecrets(config);
        return ApiResponse.success("配置已保存，部分配置需要重启服务生效", config);
    }

    private void mask(Map<String, String> config, String key) {
        if (config.containsKey(key) && !config.get(key).isEmpty()) {
            config.put(key, "****");
        }
    }

    private void validateS3Config(Map<String, String> config) {
        if (!Boolean.parseBoolean(config.get("S3_ENABLED"))) {
            return;
        }
        requireValue(config, "S3_ENDPOINT", "S3 external endpoint is required when object storage is enabled");
        requireValue(config, "S3_REGION", "S3 region is required when object storage is enabled");
        requireValue(config, "S3_BUCKET", "S3 bucket is required when object storage is enabled");
        requireValue(config, "S3_ACCESS_KEY_ID", "S3 access key is required when object storage is enabled");
        requireValue(config, "S3_SECRET_ACCESS_KEY", "S3 secret key is required when object storage is enabled");
        validateHttpEndpoint(config.get("S3_ENDPOINT"), "S3 external endpoint");
        if (Boolean.parseBoolean(config.get("S3_INTERNAL_ENDPOINT_ENABLED"))) {
            requireValue(config, "S3_INTERNAL_ENDPOINT", "S3 internal endpoint is required when internal upload is enabled");
            validateHttpEndpoint(config.get("S3_INTERNAL_ENDPOINT"), "S3 internal endpoint");
        }
    }

    private void requireValue(Map<String, String> config, String key, String message) {
        if (config.get(key) == null || config.get(key).isBlank()) {
            throw new BusinessException(message);
        }
    }

    private void validateHttpEndpoint(String value, String label) {
        try {
            URI endpoint = URI.create(value.trim());
            if (endpoint.getHost() == null
                    || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getRawUserInfo() != null
                    || endpoint.getRawFragment() != null) {
                throw new IllegalArgumentException("unsupported endpoint");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(label + " must be an absolute HTTP or HTTPS URL");
        }
    }
}
