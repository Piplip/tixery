package com.nkd.event.security;

import com.nkd.event.client.AccountClient;
import com.nkd.event.service.JwtService;
import com.nkd.event.utils.CommonUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private AccountClient accountClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String email = null, token = null;

        if(authHeader != null && authHeader.startsWith("Bearer ")){
            token = authHeader.substring(7);
            try{
                email = jwtService.extractEmail(token);
            } catch (ExpiredJwtException e){
                log.error(e.getMessage());
                String authenticatedCookie = CommonUtils.getCookieValue(request, "AUTHENTICATED");

                if(authenticatedCookie != null && authenticatedCookie.equals("true")){
                    String newToken = accountClient.getAccountJWTToken(email);
                    Cookie tokenCookie = CommonUtils.generateCookie("AUTH_TOKEN", newToken, 600);
                    response.addCookie(tokenCookie);
                    Cookie authenticated = CommonUtils.generateCookie("AUTHENTICATED", "true", 86400);
                    response.addCookie(authenticated);
                    response.setStatus(HttpServletResponse.SC_CONTINUE);
                    return;
                }

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"message\": \"Token expired\", \"redirect\": \"http://localhost:5173/login?ref=exp\"}");
                response.getWriter().flush();
                return;
            } catch (SignatureException e){
                log.error(e.getMessage());
            }
        }

        if(email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            CustomUserDetails userDetails = jwtService.buildCustomUserDetails(token);
            if(jwtService.validateToken(token)){
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }
        request.setAttribute("email", email);
        filterChain.doFilter(request, response);
    }
}
