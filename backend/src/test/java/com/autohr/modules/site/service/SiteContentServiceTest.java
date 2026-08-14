package com.autohr.modules.site.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteContentServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void partialSavePreservesFieldsThatWereNotProvided() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("title", "Original title");
        original.put("summary", "Original summary");
        original.put("content", "Original content");
        original.put("published", true);
        original.put("publishedAt", "2026-08-15 09:00");
        original.put("cover", "cover.png");
        Map<String, Object> created = service.save(original);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", created.get("id"));
        update.put("title", "Updated title");
        Map<String, Object> saved = service.save(update);

        assertEquals("Updated title", saved.get("title"));
        assertEquals("Original summary", saved.get("summary"));
        assertEquals("Original content", saved.get("content"));
        assertEquals(true, saved.get("published"));
        assertEquals("2026-08-15 09:00", saved.get("publishedAt"));
        assertEquals("cover.png", saved.get("cover"));
    }

    @Test
    void suppliedFieldsOverwriteExistingValuesAndListsUseThePageContract() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));
        Map<String, Object> created = service.save(new LinkedHashMap<>(Map.of(
                "title", "Draft", "summary", "Summary", "published", true, "publishedAt", "2026-08-15 09:00")));
        Map<String, Object> saved = service.save(new LinkedHashMap<>(Map.of(
                "id", created.get("id"), "published", false)));

        assertFalse((Boolean) saved.get("published"));
        PageResponse<Map<String, Object>> page = service.list(false, PageQuery.of(1, 1));
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getPage());
        assertEquals(1, page.getPageSize());
        assertEquals(1, page.getItems().size());
        assertEquals(1, service.list(true, PageQuery.of(1, 20)).getItems().size());
    }

    @Test
    void rejectsAnUpdateForAnUnknownContentId() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));

        assertThrows(BusinessException.class, () -> service.save(Map.of("id", 999L, "title", "Unknown")));
    }
}
