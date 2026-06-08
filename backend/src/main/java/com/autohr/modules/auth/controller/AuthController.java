package com.autohr.modules.auth.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.AuditLogVO;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<SessionUserVO> register(@Valid @RequestBody CandidateRegisterRequest request) {
        return ApiResponse.success(authService.registerCandidate(request));
    }

    @GetMapping("/me")
    public ApiResponse<SessionUserVO> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ApiResponse.success("logged out", null);
    }

    @PostMapping("/profile")
    public ApiResponse<SessionUserVO> updateProfile(Authentication authentication,
                                                    @RequestBody CandidateProfileUpdateRequest request) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        return ApiResponse.success(authService.updateCandidateProfile(current.getId(), request));
    }

    @GetMapping("/admin/users")
    public ApiResponse<List<SessionUserVO>> listUsers(Authentication authentication,
                                                      @RequestParam(required = false) String roleCode,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) String keyword) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        return ApiResponse.success(authService.listUsers(roleCode, status, keyword, current.getRoleCode()));
    }

    @PostMapping("/admin/users/{id}")
    public ApiResponse<SessionUserVO> updateUser(Authentication authentication,
                                                 @PathVariable Long id,
                                                 @RequestBody UserAdminUpdateRequest request) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        SessionUserVO updated = authService.updateUserByAdmin(id, request, current.getRoleCode());
        String action = request.getNewPassword() == null || request.getNewPassword().isBlank() ? "UPDATE_USER" : "RESET_USER_PASSWORD";
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", action, "SYS_USER", String.valueOf(updated.getId()), updated.getUsername());
        return ApiResponse.success(updated);
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<List<AuditLogVO>> listAuditLogs(@RequestParam(required = false) String moduleCode,
                                                       @RequestParam(required = false) String actionCode,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.success(auditLogService.list(moduleCode, actionCode, keyword));
    }
}
