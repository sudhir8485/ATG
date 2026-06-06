package com.nt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // Allow same-origin frames — required for H2 console which uses iframes
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // H2 console — permit all paths under /h2-console
                        .requestMatchers("/h2-console", "/h2-console/**").permitAll()
                        .requestMatchers("/", "/login", "/logout", "/shutdown-server",
                                "/css/**", "/js/**", "/images/**", "/webjars/**", "/assets/**").permitAll()
                        .requestMatchers("/admin/**", "/add-**", "/save-**", "/update-**", "/delete-**",
                                "/edit-**", "/assign-subjects/**", "/assign-batch-faculty/**",
                                "/assign-theory-faculty/**", "/auto-generate/**", "/clear-timetable",
                                "/export-xlsx",
                                "/institution-settings", "/save-institution-settings",
                                "/upload-logo", "/logo/**").hasRole("ADMIN")
                        .requestMatchers("/hod/**", "/hod-**", "/approve-**", "/approve/**", "/reject/**").hasRole("HOD")
                        .requestMatchers("/faculty/**", "/faculty-**", "/faculty-dashboard",
                                "/faculty-timetable", "/faculty-lecture/**", "/faculty-request-change").hasRole("FACULTY")
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/")
                ))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/").permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
