package com.example.anti_cheating_backend.security;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final Logger LOGGER = Logger.getLogger(JwtUtil.class.getName());

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        try {
            // Decode the Base64 secret from properties
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            
            // Ensure the key is at least 512 bits (64 bytes) for HS512
            if (keyBytes.length < 64) {
                // Pad the key to 64 bytes if it's too short
                byte[] paddedKey = new byte[64];
                System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 64));
                LOGGER.warning("JWT secret key was too short, padded to 512 bits");
                return Keys.hmacShaKeyFor(paddedKey);
            }
            
            LOGGER.info("Using configured JWT secret key (stable across restarts)");
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            // If Base64 decoding fails, use the secret as-is (UTF-8 bytes)
            LOGGER.warning("Secret is not valid Base64, using as UTF-8 string: " + e.getMessage());
            byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            // Pad if necessary
            if (keyBytes.length < 64) {
                byte[] paddedKey = new byte[64];
                System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 64));
                return Keys.hmacShaKeyFor(paddedKey);
            }
            
            return Keys.hmacShaKeyFor(keyBytes);
        }
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        SecretKey signingKey = getSigningKey();

        LOGGER.info("Generating JWT for subject: " + subject + ", expires: " + expiryDate);
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
        LOGGER.info("JWT generated successfully");
        return token;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            LOGGER.severe("Failed to parse JWT: " + e.getMessage());
            throw e;
        }
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}