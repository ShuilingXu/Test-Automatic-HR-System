package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AuthBootstrapRunner implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        ensureUser("itadmin", "123456", "IT_ADMIN", "IT管理员");
        ensureUser("hradmin", "123456", "HR_ADMIN", "HR管理员");
        ensureUser("hruser", "123456", "HR_USER", "HR用户");
        ensureInterviewAiCommentColumn();
    }

    private void ensureUser(String username, String password, String roleCode, String displayName) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setId(nextId(sysUserMapper.selectList(null).stream().map(SysUser::getId).toList()));
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoleCode(roleCode);
        user.setDisplayName(displayName);
        user.setStatus(1);
        user.setProfileCompleted(1);
        user.setTokenVersion(0);
        sysUserMapper.insert(user);
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }

    private void ensureInterviewAiCommentColumn() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE interview_ai_record ADD COLUMN interviewer_comment VARCHAR(2000)");
        } catch (SQLException ignored) {
        }
    }
}
