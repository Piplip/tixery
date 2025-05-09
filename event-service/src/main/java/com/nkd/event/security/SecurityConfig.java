package com.nkd.event.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> {
                    exception.authenticationEntryPoint((request, response, authException) -> {
                        log.error(authException.toString());
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.getWriter().write("Unauthorized");
                    });
                    exception.accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("Forbidden");
                    });
                })
//                .authorizeHttpRequests(request ->
//                        request.requestMatchers("/error/**", "/get/specific", "/get/related", "/get/profile", "/get/suggested", "/event/report",
//                                        "/ws/**", "/actuator/**", "/search/suggestions", "/search", "/search/trends", "/seat-map/state/update",
//                                        "/event/trends", "/events/**").permitAll()
//                                .requestMatchers("/create/**", "/delete", "/tickets/**").hasRole("HOST")
//                                .requestMatchers("/order/tickets").hasRole("ATTENDEE")
//                                .anyRequest().authenticated()
//                )
                .authorizeHttpRequests(request -> request
                        .requestMatchers(
                                "/error/**", "/actuator/**", "/ws/**",
                                "/search", "/search/suggestions", "/search/trends",
                                "/get/specific", "/get/related", "/get/profile", "/get/suggested",
                                "/events/**", "/event/trends", "/seat-map/state/update",
                                "/seat-map/data", "/event/report"
                        ).permitAll()

                        .requestMatchers(
                                "/create/**", "/update/**", "/delete/**",
                                "/tickets/add", "/tickets/update", "/tickets/remove",
                                "/tickets/tier/**", "/tier-tickets",
                                "/event/dashboard", "/event/attendees", "/attendees/email",
                                "/organizer/report", "/event/report"
                        ).hasRole("HOST")

                        .requestMatchers(
                                "/payment/**", "/coupon/**",
                                "/search/orders", "/orders/profile/**",
                                "/order/cancel"
                        ).hasAnyRole("ATTENDEE", "HOST")

                        .requestMatchers(
                                "/order/tickets", "/ticket/download",
                                "/event/favorite/**", "/attendee/interaction"
                        ).hasRole("ATTENDEE")

                        .requestMatchers("/event/favorite/total").hasAnyRole("INTERNAL", "ATTENDEE")

                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        .requestMatchers("/search/history/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean(name = "corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4001", "http://localhost:9090"));
        configuration.setAllowedMethods(List.of("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
