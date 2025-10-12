package com.example.anti_cheating_backend.security;

import java.io.IOException;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(JwtAuthenticationFilter.class.getName());

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String authorizationHeader = request.getHeader("Authorization");
        
        LOGGER.info("JWT Filter - Request URI: " + request.getRequestURI());
        LOGGER.info("JWT Filter - Authorization Header: " + (authorizationHeader != null ? "Present" : "Missing"));

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            LOGGER.info("JWT Filter - Extracted token (first 20 chars): " + jwt.substring(0, Math.min(20, jwt.length())) + "...");
            try {
                username = jwtUtil.extractUsername(jwt);
                LOGGER.info("JWT Filter - Extracted username: " + username);
            } catch (Exception e) {
                LOGGER.severe("JWT Filter - Failed to extract username: " + e.getMessage());
            }
        } else {
            LOGGER.warning("JWT Filter - Authorization header missing or doesn't start with 'Bearer '");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                LOGGER.info("JWT Filter - User details loaded for: " + username);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken
                            .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    LOGGER.info("JWT Filter - Authentication successful for user: " + username + " with roles: " + userDetails.getAuthorities());
                } else {
                    LOGGER.warning("JWT Filter - Token validation failed for user: " + username);
                }
            } catch (Exception e) {
                LOGGER.severe("JWT Filter - Error during authentication: " + e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}