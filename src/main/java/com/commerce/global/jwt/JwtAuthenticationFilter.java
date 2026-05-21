package com.commerce.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                Claims claims = jwtProvider.parse(token);

                String type = claims.get("type", String.class);
                if (!"access".equals(type)) {
                    throw new JwtException("Invalid token type");
                }

                String role = claims.get("role", String.class);
                if (role == null) {
                    throw new JwtException("Role not found");
                }

                Long memberId;
                try {
                    memberId = Long.parseLong(claims.getSubject());
                } catch (NumberFormatException e) {
                    throw new JwtException("Invalid memberId");
                }

                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                new AuthMember(memberId, role),
                                null,
                                List.of(new SimpleGrantedAuthority(authority))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException e) {
                // 여기서 직접 응답하지 않음 — filterChain을 계속 진행시켜
                // SecurityConfig의 authenticationEntryPoint가 401 JSON을 반환하도록 위임
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}