package com.autohr.modules.site.service;

import com.autohr.modules.site.dto.SiteSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class SiteSettingsService {

    private static final SiteSettings DEFAULT_SETTINGS = new SiteSettings(
            "", "千早爱音",
            "让合适的人，找到值得投入的事。了解团队、浏览机会，开启你的下一段职业旅程。",
            "千早爱音 · 人才与组织中心");

    private final ObjectMapper objectMapper;
    private final Path settingsPath;

    @Autowired
    public SiteSettingsService(ObjectMapper objectMapper,
                               @Value("${site-settings.path:.site-settings.json}") String settingsPath) {
        this(objectMapper, Paths.get(settingsPath));
    }

    SiteSettingsService(ObjectMapper objectMapper, Path settingsPath) {
        this.objectMapper = objectMapper;
        this.settingsPath = settingsPath;
    }

    public synchronized SiteSettings get() {
        if (!Files.exists(settingsPath)) {
            return DEFAULT_SETTINGS;
        }
        try {
            return normalize(objectMapper.readValue(
                    Files.readString(settingsPath, StandardCharsets.UTF_8), SiteSettings.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read site settings", ex);
        }
    }

    public synchronized SiteSettings save(SiteSettings incoming) {
        SiteSettings settings = normalize(incoming);
        Path absolutePath = settingsPath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        Path temporaryPath = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporaryPath = Files.createTempFile(parent, ".site-settings-", ".tmp");
            Files.writeString(temporaryPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporaryPath, absolutePath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryPath, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return settings;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save site settings", ex);
        } finally {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException ignored) {
                    // Runtime cleanup can remove an abandoned temporary file later.
                }
            }
        }
    }

    private SiteSettings normalize(SiteSettings settings) {
        if (settings == null) {
            return DEFAULT_SETTINGS;
        }
        return new SiteSettings(
                cleanLogoUrl(settings.logoUrl()),
                cleanRequired(settings.siteTitle(), DEFAULT_SETTINGS.siteTitle()),
                cleanRequired(settings.siteSubtitle(), DEFAULT_SETTINGS.siteSubtitle()),
                cleanOptional(settings.footerHtml()));
    }

    private String cleanLogoUrl(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned.startsWith("/") && !cleaned.startsWith("//") && !cleaned.contains("\\")) {
            return cleaned;
        }
        try {
            return "https".equalsIgnoreCase(java.net.URI.create(cleaned).getScheme()) ? cleaned : "";
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String cleanRequired(String value, String fallback) {
        String cleaned = cleanOptional(value);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String cleanOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
