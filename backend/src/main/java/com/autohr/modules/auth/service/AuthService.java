package com.autohr.modules.auth.service;

import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;

import java.util.List;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    SessionUserVO registerCandidate(CandidateRegisterRequest request);
    SessionUserVO getCurrentUser();
    SessionUserVO updateCandidateProfile(Long userId, CandidateProfileUpdateRequest request);
    SessionUserVO loadUserByUsername(String username);
    List<SessionUserVO> listUsers(String roleCode, Integer status, String keyword, String operatorRoleCode);
    SessionUserVO updateUserByAdmin(Long id, UserAdminUpdateRequest request, String operatorRoleCode);
    void logout(String username);
}
