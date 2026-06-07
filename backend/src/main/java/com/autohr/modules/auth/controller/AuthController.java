package com.autohr.modules.auth.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/profile")
    public ApiResponse<SessionUserVO> updateProfile(Authentication authentication,
                                                    @RequestBody CandidateProfileUpdateRequest request) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        return ApiResponse.success(authService.updateCandidateProfile(current.getId(), request));
    }
}
