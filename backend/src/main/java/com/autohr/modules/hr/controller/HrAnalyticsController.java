package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.hr.dto.DashboardConfigRequest;
import com.autohr.modules.hr.dto.HrStatisticsVO;
import com.autohr.modules.hr.service.HrStatisticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController @RequestMapping("/api/hr") @RequiredArgsConstructor
public class HrAnalyticsController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DEFAULT_CONFIG = "{\"cards\":[\"employeeCount\",\"departmentCount\",\"openJobCount\",\"newHireCount\",\"dismissalCount\",\"averageGross\"],\"charts\":{\"salary\":\"bar\",\"recruitment\":\"pie\",\"dismissal\":\"pie\",\"department\":\"bar\"}}";
    private static final Set<String> CARD_IDS = Set.of("employeeCount", "activeEmployeeCount", "departmentCount",
            "openJobCount", "newHireCount", "dismissalCount", "averageGross", "grossTotal", "salaryGrowth",
            "candidateCount", "interviewingCount", "passedCount", "dismissalAverage", "departmentAverageCount");
    private static final Set<String> CHART_KEYS = Set.of("salary", "recruitment", "dismissal", "department");
    private static final Set<String> CHART_TYPES = Set.of("bar", "pie", "table");
    private final HrStatisticsService statisticsService;
    private final AuthService authService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @GetMapping("/statistics") public ApiResponse<HrStatisticsVO> statistics(@RequestParam(required=false) String month){return ApiResponse.success(statisticsService.statistics(month==null?YearMonth.now(BUSINESS_ZONE).toString():month));}
    @GetMapping("/dashboard/config") public ApiResponse<Map<String,Object>> config(Authentication auth){Long userId=user(auth).getId();List<Map<String,Object>> rows=jdbc.queryForList("SELECT config_json FROM user_dashboard_config WHERE user_id=?",userId);return ApiResponse.success(Map.<String,Object>of("configJson",rows.isEmpty()?DEFAULT_CONFIG:rows.get(0).get("config_json")));}
    @PostMapping("/dashboard/config") public ApiResponse<Void> saveConfig(Authentication auth,@Valid @RequestBody DashboardConfigRequest request){validateConfig(request.getConfigJson());Long userId=user(auth).getId();int changed=jdbc.update("UPDATE user_dashboard_config SET config_json=? WHERE user_id=?",request.getConfigJson(),userId);if(changed==0)jdbc.update("INSERT INTO user_dashboard_config (user_id,config_json) VALUES (?,?)",userId,request.getConfigJson());return ApiResponse.success("saved",null);}
    private void validateConfig(String json){try{JsonNode root=objectMapper.readTree(json);if(root==null||!root.isObject()||root.size()!=2||!root.has("cards")||!root.has("charts"))throw invalidConfig();JsonNode cards=root.get("cards");if(!cards.isArray())throw invalidConfig();for(JsonNode card:cards)if(!card.isTextual()||!CARD_IDS.contains(card.textValue()))throw invalidConfig();JsonNode charts=root.get("charts");if(!charts.isObject()||charts.size()!=CHART_KEYS.size())throw invalidConfig();for(String key:CHART_KEYS){JsonNode type=charts.get(key);if(type==null||!type.isTextual()||!CHART_TYPES.contains(type.textValue()))throw invalidConfig();}}catch(BusinessException e){throw e;}catch(Exception e){throw invalidConfig();}}
    private BusinessException invalidConfig(){return new BusinessException("Dashboard config has an invalid shape or value");}
    private SessionUserVO user(Authentication auth){return authService.loadUserByUsername(auth.getName());}
}
