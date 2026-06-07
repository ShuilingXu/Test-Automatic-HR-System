package com.autohr.modules.auth.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;
import com.autohr.modules.auth.service.AuthService;
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
        return ApiResponse.success(authService.updateUserByAdmin(id, request, current.getRoleCode()));
    }
}
