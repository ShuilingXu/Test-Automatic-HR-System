package com.autohr.config;

import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/register/code", "/api/auth/captcha").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recruitment/jobs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recruitment/candidates/**").hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers("/api/auth/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "IT_ADMIN", "HR_ADMIN")
                        .requestMatchers("/api/hr/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/recruitment/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/interview/it/**").hasAnyAuthority("ROLE_IT_ADMIN", "IT_ADMIN")
                        .requestMatchers("/api/interview/hr/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/interview/interviewee/**").hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers("/api/auth/me", "/api/auth/profile", "/api/auth/logout").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
