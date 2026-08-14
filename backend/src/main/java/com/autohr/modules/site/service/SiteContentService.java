package com.autohr.modules.site.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small file-backed store for the public information pages. Content is independent from .env. */
@Service
public class SiteContentService {

    private final ObjectMapper objectMapper;
    private final Path contentPath;

    @Autowired
    public SiteContentService(ObjectMapper objectMapper,
                              @Value("${site-content.path:.site-content.json}") String contentPath) {
        this(objectMapper, Paths.get(contentPath));
    }

    SiteContentService(ObjectMapper objectMapper, Path contentPath) {
        this.objectMapper = objectMapper;
        this.contentPath = contentPath;
    }

    public synchronized PageResponse<Map<String, Object>> list(boolean publishedOnly, PageQuery pageQuery) {
        List<Map<String, Object>> items = read();
        List<Map<String, Object>> filteredItems = items.stream()
                .filter(item -> !publishedOnly || Boolean.TRUE.equals(item.get("published")))
                .sorted(Comparator.comparing(item -> String.valueOf(item.getOrDefault("publishedAt", "")), Comparator.reverseOrder()))
                .toList();
        return PageResponse.slice(filteredItems, pageQuery);
    }

    public synchronized Map<String, Object> save(Map<String, Object> incoming) {
        List<Map<String, Object>> items = read();
        Map<String, Object> updates = incoming == null ? Map.of() : incoming;
        long id = number(updates.get("id"));
        long requestedId = id;
        Map<String, Object> existingItem = id == 0 ? null : items.stream()
                .filter(item -> number(item.get("id")) == requestedId)
                .findFirst()
                .orElse(null);
        if (id != 0 && existingItem == null) {
            throw new BusinessException("Site content does not exist. Create it without an id.");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        if (existingItem != null) {
            item.putAll(existingItem);
        }
        item.putAll(updates);
        if (id == 0) {
            id = items.stream().mapToLong(existing -> number(existing.get("id"))).max().orElse(0) + 1;
        }
        item.put("id", id);
        item.putIfAbsent("type", "announcement");
        item.putIfAbsent("published", Boolean.FALSE);
        item.putIfAbsent("publishedAt", "");
        item.putIfAbsent("summary", "");
        item.putIfAbsent("content", "");
        item.putIfAbsent("cover", "");
        final long savedId = id;
        items.removeIf(existing -> number(existing.get("id")) == savedId);
        items.add(item);
        write(items);
        return item;
    }

    public synchronized void delete(long id) {
        List<Map<String, Object>> items = read();
        items.removeIf(item -> number(item.get("id")) == id);
        write(items);
    }

    private List<Map<String, Object>> read() {
        if (!Files.exists(contentPath)) {
            return defaultContent();
        }
        try {
            return objectMapper.readValue(Files.readString(contentPath, StandardCharsets.UTF_8), new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("读取站点内容失败", ex);
        }
    }

    private void write(List<Map<String, Object>> items) {
        try {
            Files.writeString(contentPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("保存站点内容失败", ex);
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private List<Map<String, Object>> defaultContent() {
        Map<String, Object> welcome = new LinkedHashMap<>();
        welcome.put("id", 1L);
        welcome.put("type", "announcement");
        welcome.put("title", "欢迎来到千早爱音人才中心");
        welcome.put("summary", "关注职位动态，了解团队与面试流程");
        welcome.put("content", "这里是企业招聘信息与最新通知的统一入口。管理员可以在后台编辑和发布内容。");
        welcome.put("cover", "");
        welcome.put("published", true);
        welcome.put("publishedAt", "2026-08-13 09:00");
        return new ArrayList<>(List.of(welcome));
    }
}
