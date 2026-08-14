package com.autohr.modules.hr.controller;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.hr.dto.DashboardConfigRequest;
import com.autohr.modules.hr.service.HrStatisticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrAnalyticsControllerTest {
    @TempDir Path tempDirectory;

    @Test
    void dashboardConfigPersistsOneRowAndRejectsMalformedShapes() {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("dashboard.db").toString().replace('\\', '/');
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url));
        jdbc.execute("CREATE TABLE user_dashboard_config (id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL UNIQUE,config_json TEXT NOT NULL)");
        AuthService authService = mock(AuthService.class);
        Authentication authentication = mock(Authentication.class);
        SessionUserVO user = new SessionUserVO();
        user.setId(7L);
        when(authentication.getName()).thenReturn("hr-admin");
        when(authService.loadUserByUsername("hr-admin")).thenReturn(user);
        HrAnalyticsController controller = new HrAnalyticsController(mock(HrStatisticsService.class), authService, jdbc, new ObjectMapper());

        DashboardConfigRequest request = request("{\"cards\":[\"employeeCount\"],\"charts\":{\"salary\":\"table\",\"recruitment\":\"pie\",\"dismissal\":\"bar\",\"department\":\"bar\"}}");
        controller.saveConfig(authentication, request);
        request.setConfigJson("{\"cards\":[\"averageGross\"],\"charts\":{\"salary\":\"bar\",\"recruitment\":\"pie\",\"dismissal\":\"pie\",\"department\":\"table\"}}");
        controller.saveConfig(authentication, request);

        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM user_dashboard_config WHERE user_id=7", Long.class));
        assertEquals(request.getConfigJson(), controller.config(authentication).getData().get("configJson"));
        assertThrows(BusinessException.class, () -> controller.saveConfig(authentication, request("null")));
        assertThrows(BusinessException.class, () -> controller.saveConfig(authentication,
                request("{\"cards\":[\"unknown\"],\"charts\":{\"salary\":\"line\"}}")));
    }

    private DashboardConfigRequest request(String json) {
        DashboardConfigRequest request = new DashboardConfigRequest();
        request.setConfigJson(json);
        return request;
    }
}
