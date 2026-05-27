package com.salesmap.backend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            CustomUserDetailsService customUserDetailsService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        boolean debugTarget = isDebugTarget(requestUri);
        String token = resolveToken(request);

        if (debugTarget) {
            log.debug("[JWT DEBUG] uri={}, authorizationHeaderPresent={}, bearerTokenPresent={}",
                    requestUri,
                    request.getHeader(AUTHORIZATION_HEADER) != null,
                    token != null
            );
        }

        boolean validToken = token != null && jwtTokenProvider.validateToken(token);

        if (debugTarget) {
            log.debug("[JWT DEBUG] uri={}, tokenValid={}", requestUri, validToken);
        }

        if (validToken) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (debugTarget) {
                    log.debug("[JWT DEBUG] uri={}, authenticationSet=true, userId={}, principalType={}",
                            requestUri,
                            userId,
                            userDetails.getClass().getName()
                    );
                }
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();

                if (debugTarget) {
                    log.debug("[JWT DEBUG] uri={}, authenticationSet=false, reason={}",
                            requestUri,
                            exception.getMessage(),
                            exception
                    );
                }
            }
        } else if (debugTarget) {
            log.debug("[JWT DEBUG] uri={}, authenticationSet=false", requestUri);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private boolean isDebugTarget(String requestUri) {
        return "/api/salesmap/register".equals(requestUri) || "/api/analysis".equals(requestUri);
    }
}
