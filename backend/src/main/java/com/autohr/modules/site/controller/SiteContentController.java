package com.autohr.modules.site.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.site.service.SiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/site-content")
@RequiredArgsConstructor
public class SiteContentController {

    private final SiteContentService siteContentService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> published() {
        return ApiResponse.success(siteContentService.list(true));
    }

    @GetMapping("/admin")
    public ApiResponse<List<Map<String, Object>>> all() {
        return ApiResponse.success(siteContentService.list(false));
    }

    @PostMapping("/admin")
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> content) {
        return ApiResponse.success(siteContentService.save(content));
    }

    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        siteContentService.delete(id);
        return ApiResponse.success(null);
    }
}
