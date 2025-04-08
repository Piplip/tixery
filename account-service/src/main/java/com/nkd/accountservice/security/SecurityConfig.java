package com.nkd.accountservice.security;

import com.nkd.accountservice.security.oauth2.CustomOAuth2SuccessHandler;
import com.nkd.accountservice.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DSLContext context;
    private final JwtFilter jwtFilter;
    private final JwtService jwtService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(expception -> {
                     expception.authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Unauthorized");
                    });
                    expception.accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("Forbidden");
                    });
                })
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Client(Customizer.withDefaults())
                .oauth2Login(oauth2Login -> {
                    oauth2Login.successHandler(new CustomOAuth2SuccessHandler(context, jwtService));
                    oauth2Login.failureUrl("http://localhost:5173/login?ref=user_cancel");
                })
                .authorizeHttpRequests(request ->
//                    request
//                        .requestMatchers("/sign-up", "/login", "/check-email", "/activate", "/resend-activation", "/profile/setup", "/get/jwt"
//                                , "/actuator/**", "/profile/create", "/oauth2/authorization/**", "/forgot-password/**", "/organizer/profile/get").permitAll()
//                        .requestMatchers("/organizer/profile", "/organizer/profile/create").hasRole("HOST")
//                        .requestMatchers("/internal//**").hasRole("INTERNAL")
//                        .anyRequest().authenticated()
                                request.requestMatchers("/sign-up", "/login", "/check-email", "/activate", "/resend-activation",
                                                "/profile/setup", "/profile/create", "/forgot-password/**",
                                                "/oauth2/authorization/**", "/organizer/profile/get").permitAll()
                                        .requestMatchers("/actuator/**").permitAll()
                                        .requestMatchers("/profile/oauth/create", "/profile/oauth/update", "/oauth2/set-password", "/oauth2/set-password/**").permitAll()
                                        .requestMatchers("/internal/**", "/get/jwt").hasRole("INTERNAL")
                                        .requestMatchers("/organizer/profile", "/organizer/profile/create", "/organizer/profile/update", "/organizer/profile/delete",
                                                "/organizer/profile/custom-url/check", "/organizer/profile/update/total-followers").hasRole("HOST")
                                        .requestMatchers("/attendee/**", "/follow/**").hasAnyRole("ATTENDEE", "HOST")
                                        .requestMatchers("/admin/**").hasRole("ADMIN")
                                        .anyRequest().authenticated()
                )
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean(name = "corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4001", "http://localhost:9090"));
        configuration.setAllowedMethods(List.of("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CustomUserDetailService customUserDetailService(){
        return new CustomUserDetailService(context);
    }

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailService customUserDetailService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(customUserDetailService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

}
