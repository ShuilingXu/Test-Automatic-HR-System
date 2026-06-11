package com.autohr.modules.system;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemConfigService {

    private static final String ENV_FILE = ".env";

    public Map<String, String> loadConfig(String[] keys) {
        Map<String, String> env = readEnvFile();
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : keys) {
            result.put(key, env.getOrDefault(key, ""));
        }
        return result;
    }

    public void saveConfig(Map<String, String> updates) {
        Map<String, String> env = readEnvFile();
        env.putAll(updates);
        writeEnvFile(env);
    }

    private Map<String, String> readEnvFile() {
        Map<String, String> env = new LinkedHashMap<>();
        Path path = Path.of(ENV_FILE);
        if (!Files.exists(path)) {
            try {
                Path fallback = Path.of("..", ENV_FILE);
                if (Files.exists(fallback)) path = fallback;
                else return env;
            } catch (Exception e) {
                return env;
            }
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    env.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
                }
            }
        } catch (IOException ignored) {
        }
        return env;
    }

    private void writeEnvFile(Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        try {
            Path path = Path.of(ENV_FILE);
            Path fallback = Path.of("..", ENV_FILE);
            if (!Files.exists(path) && Files.exists(fallback)) {
                path = fallback;
            }
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("写入配置文件失败: " + ex.getMessage());
        }
    }
}
