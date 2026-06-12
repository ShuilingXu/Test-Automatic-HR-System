package com.autohr.modules.system.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemConfigService {

    private final Path envPath = Paths.get(".env");

    public synchronized Map<String, String> loadConfig(String... keys) {
        Map<String, String> envFile = readEnvFile();
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : keys) {
            String value = envFile.get(key);
            if (value == null) {
                value = System.getenv(key);
            }
            result.put(key, value == null ? "" : value);
        }
        return result;
    }

    public synchronized void saveConfig(Map<String, String> updates) {
        Map<String, String> current = readEnvFile();
        updates.forEach((key, value) -> {
            if (StrUtil.isNotBlank(key) && value != null) {
                current.put(key, value);
            }
        });
        writeEnvFile(current);
    }

    private Map<String, String> readEnvFile() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(envPath)) {
            return values;
        }
        try {
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int index = trimmed.indexOf('=');
                values.put(trimmed.substring(0, index).trim(), trimmed.substring(index + 1).trim());
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("读取.env配置失败", ex);
        }
    }

    private void writeEnvFile(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append(System.lineSeparator());
        }
        try {
            Files.writeString(envPath, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("写入.env配置失败", ex);
        }
    }
}
