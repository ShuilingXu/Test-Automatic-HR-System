package com.autohr.config;

import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
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

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob: https:; font-src 'self' data:; media-src 'self' data: blob: https:; "
            + "connect-src 'self' https: wss:; worker-src 'self' blob:; child-src 'self' blob:";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/register/code", "/api/auth/password-reset", "/api/auth/password-reset/code", "/api/auth/captcha").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recruitment/jobs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/site-content").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/site-settings").permitAll()
                        .requestMatchers("/api/site-settings/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "IT_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/recruitment/candidates/mine")
                        .hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers(HttpMethod.GET, "/api/recruitment/resumes/**",
                                "/api/interview/runtime-config", "/api/interview/ice-servers")
                        .hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "ROLE_INTERVIEWEE",
                                "IT_ADMIN", "HR_ADMIN", "HR_USER", "INTERVIEWEE")
                        .requestMatchers("/api/site-content/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "IT_ADMIN", "HR_ADMIN")
                        .requestMatchers("/api/system/**").hasAnyAuthority("ROLE_IT_ADMIN", "IT_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/recruitment/candidates/**").hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers("/api/auth/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "IT_ADMIN", "HR_ADMIN")
                        .requestMatchers("/api/hr/payroll/**", "/api/hr/statistics/**", "/api/hr/dashboard/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/hr/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/recruitment/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/interview/it/**").hasAnyAuthority("ROLE_IT_ADMIN", "IT_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/interview/hr/process-templates/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/interview/hr/process-templates/**").hasAnyAuthority("ROLE_HR_ADMIN", "HR_ADMIN")
                        .requestMatchers("/api/interview/hr/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/interview/interviewee/**").hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers("/api/auth/me", "/api/auth/profile", "/api/auth/logout", "/api/auth/change-password").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
