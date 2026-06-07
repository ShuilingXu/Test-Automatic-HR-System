package com.autohr.modules.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.JwtService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = requireUserByUsername(request.getUsername());
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException("账号已停用");
        }
        LoginResponse response = new LoginResponse();
        response.setToken(jwtService.generateToken(user));
        response.setUser(toSessionUser(user));
        return response;
    }

    @Override
    @Transactional
    public SessionUserVO registerCandidate(CandidateRegisterRequest request) {
        ensureUniqueUsername(request.getUsername());
        SysUser user = new SysUser();
        user.setId(nextId(sysUserMapper.selectList(null).stream().map(SysUser::getId).toList()));
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoleCode("INTERVIEWEE");
        user.setDisplayName(request.getDisplayName());
        user.setMobilePhone(request.getMobilePhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setProfileCompleted(0);
        sysUserMapper.insert(user);
        return toSessionUser(user);
    }

    @Override
    public SessionUserVO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || StrUtil.equals(authentication.getName(), "anonymousUser")) {
            throw new BusinessException("未登录");
        }
        return loadUserByUsername(authentication.getName());
    }

    @Override
    @Transactional
    public SessionUserVO updateCandidateProfile(Long userId, CandidateProfileUpdateRequest request) {
        SysUser user = requireUser(userId);
        user.setDisplayName(StrUtil.blankToDefault(request.getDisplayName(), user.getDisplayName()));
        user.setMobilePhone(StrUtil.blankToDefault(request.getMobilePhone(), user.getMobilePhone()));
        user.setEmail(request.getEmail());
        user.setProfileCompleted(1);
        sysUserMapper.updateById(user);
        return toSessionUser(user);
    }

    @Override
    public SessionUserVO loadUserByUsername(String username) {
        return toSessionUser(requireUserByUsername(username));
    }

    @Override
    public List<SessionUserVO> listUsers(String roleCode, Integer status, String keyword) {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(StrUtil.isNotBlank(roleCode), SysUser::getRoleCode, roleCode)
                .eq(status != null, SysUser::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getDisplayName, keyword)
                        .or().like(SysUser::getMobilePhone, keyword))
                .orderByAsc(SysUser::getId))
                .stream().map(this::toSessionUser).toList();
    }

    @Override
    @Transactional
    public SessionUserVO updateUserByAdmin(Long id, UserAdminUpdateRequest request) {
        SysUser user = requireUser(id);
        if (StrUtil.isNotBlank(request.getRoleCode())) {
            user.setRoleCode(request.getRoleCode());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getMobilePhone() != null) {
            user.setMobilePhone(request.getMobilePhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        sysUserMapper.updateById(user);
        return toSessionUser(user);
    }

    private void ensureUniqueUsername(String username) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
    }

    private SysUser requireUserByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        return user;
    }

    private SessionUserVO toSessionUser(SysUser user) {
        SessionUserVO vo = new SessionUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
