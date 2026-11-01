package com.example.testfinal.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT authentication filter for stateless authentication.
 * Extracts and validates JWT from Authorization header.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final Key signingKey;
    private final String issuer;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(@org.springframework.beans.factory.annotation.Value("${app.jwt.secret}") String secret,
                                   @org.springframework.beans.factory.annotation.Value("${app.jwt.issuer:companion}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = extractJwtFromRequest(request);
            
            if (jwt != null && validateToken(jwt)) {
                Authentication authentication = getAuthentication(jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            // Log but don't block - let security config handle unauthorized access
            logger.warn("JWT authentication failed: {}", ex.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseClaimsJws(token)
            .getBody();

        String username = claims.getSubject();
        List<String> roles = claims.get("roles", List.class);
        
        Collection<? extends GrantedAuthority> authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toList());

        UserPrincipal principal = new UserPrincipal(username, "", authorities);
        
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(null));
        
        return authentication;
    }

    /**
     * User principal for JWT authentication.
     */
    public record UserPrincipal(String username, String password, Collection<? extends GrantedAuthority> authorities)
            implements UserDetails {
        
        @Override
        public String getUsername() { return username; }
        
        @Override
        public String getPassword() { return password; }
        
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
        
        @Override
        public boolean isAccountNonExpired() { return true; }
        
        @Override
        public boolean isAccountNonLocked() { return true; }
        
        @Override
        public boolean isCredentialsNonExpired() { return true; }
        
        @Override
        public boolean isEnabled() { return true; }
    }
}