package com.example.anti_cheating_backend.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.anti_cheating_backend.security.JwtAuthenticationEntryPoint;
import com.example.anti_cheating_backend.security.JwtAuthenticationFilter;
import com.example.anti_cheating_backend.security.OAuth2AuthenticationFailureHandler;
import com.example.anti_cheating_backend.security.OAuth2AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          @Lazy JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Lazy OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                          @Lazy OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS with configuration
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/api/auth/**").permitAll();
                    
                    // Desktop Monitor endpoints
                    auth.requestMatchers("/api/desktop-monitor/authenticate", "/api/desktop-monitor/status").permitAll();
                    // Students can upload screenshots and log activity during exams
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/desktop-monitor/screenshot").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN", "ROLE_PROCTOR");
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/desktop-monitor/activity").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN", "ROLE_PROCTOR");
                    // Students can view their own screenshots and activities (MUST come before the admin-only /screenshots/** rule)
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/desktop-monitor/screenshots/student/**").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN", "ROLE_PROCTOR");
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/desktop-monitor/activities/student/**").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN", "ROLE_PROCTOR");
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/desktop-monitor/screenshots/*/download").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN");
                    // Admin-only screenshot/activity retrieval endpoints
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/desktop-monitor/screenshots/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/desktop-monitor/activities/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/desktop-monitor/**").hasAuthority("ROLE_ADMIN");
                    
                    // Student-accessible exam endpoints (public GET routes)
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/exams/published").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/exams/ongoing").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN");
                    // Admin-only endpoints (creation, modification, deletion)
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/exams").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/exams/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/exams/**").hasAuthority("ROLE_ADMIN");
                    // Other admin endpoints
                    auth.requestMatchers("/api/students", "/api/students/**").hasAuthority("ROLE_ADMIN");
                    // Test & Results endpoints
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/test/**").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/test/*/submit").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/test/results/*/grade").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/test/results/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/test").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/test/**").hasAuthority("ROLE_ADMIN");
                    
                    // Session endpoints - students can create/view their own sessions
                    auth.requestMatchers("/api/sessions/**").hasAnyAuthority("ROLE_STUDENT", "ROLE_ADMIN");
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                    auth.requestMatchers("/health", "/error", "/ws/**").permitAll();
                    auth.anyRequest().authenticated();
                });

            http.oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(oAuth2AuthenticationFailureHandler));

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200", "http://127.0.0.1:4200"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}