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
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null) {
            if (passwordEncoder.matches(password, existing.getPassword()) && !Integer.valueOf(1).equals(existing.getMustChangePassword())) {
                existing.setMustChangePassword(1);
                sysUserMapper.updateById(existing);
            }
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoleCode(roleCode);
        user.setDisplayName(displayName);
        user.setStatus(1);
        user.setProfileCompleted(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(1);
        sysUserMapper.insert(user);
    }

    private void ensureInterviewAiCommentColumn() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE interview_ai_record ADD COLUMN interviewer_comment VARCHAR(2000)");
        } catch (SQLException ignored) {
        }
    }
}
