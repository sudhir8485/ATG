package com.nt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // RegexRequestMatcher bypasses MVC-aware matching so the H2 console servlet is permitted
                        .requestMatchers(new RegexRequestMatcher("/h2-console.*", null)).permitAll()
                        .requestMatchers("/", "/login", "/logout", "/shutdown-server",
                                "/css/**", "/js/**", "/images/**", "/webjars/**", "/assets/**").permitAll()
                        .requestMatchers("/admin/**", "/add-**", "/save-**", "/update-**", "/delete-**",
                                "/edit-**", "/assign-subjects/**", "/assign-batch-faculty/**",
                                "/assign-theory-faculty/**", "/auto-generate/**", "/clear-timetable",
                                "/export-xlsx", "/print-timetable",
                                "/institution-settings", "/save-institution-settings",
                                "/upload-logo", "/logo/**").hasRole("ADMIN")
                        .requestMatchers("/hod/**", "/hod-**", "/approve-**", "/approve/**", "/reject/**").hasRole("HOD")
                        .requestMatchers("/faculty/**", "/faculty-**", "/faculty-dashboard",
                                "/faculty-timetable", "/faculty-lecture/**", "/faculty-request-change").hasRole("FACULTY")
                        .anyRequest().authenticated()
                )
                // we keep the existing custom login controller; disable Spring's default form login page/processing
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/")
                ))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/").permitAll());
        return http.build();
    }

    // passwords are stored as plain text today; keep behavior but make encoder explicit
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
