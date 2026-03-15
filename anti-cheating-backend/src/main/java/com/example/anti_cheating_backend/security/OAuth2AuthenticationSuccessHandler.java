package com.example.anti_cheating_backend.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.anti_cheating_backend.service.AuthService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.frontend.oauth2.redirect:http://localhost:4200/oauth2/callback}")
    private String frontendRedirectUrl;

    public OAuth2AuthenticationSuccessHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> auth = authService.handleGoogleOAuthLogin(oauth2User);

        String redirectUrl = frontendRedirectUrl
                + "?token=" + encode(auth.get("token"))
                + "&userId=" + encode(auth.get("userId"))
                + "&userName=" + encode(auth.get("userName"))
                + "&email=" + encode(auth.get("email"))
                + "&role=" + encode(auth.get("role"));

        response.sendRedirect(redirectUrl);
    }

    private String encode(Object value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }
}
