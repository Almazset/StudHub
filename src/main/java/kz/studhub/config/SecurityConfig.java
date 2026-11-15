package kz.studhub.config;

import kz.studhub.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import kz.studhub.security.*;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean AuthRateLimiter authRateLimiter() { return new AuthRateLimiter(); }

    @Bean DeviceSessionRegistry sessionRegistry() { return new DeviceSessionRegistry(); }
    @Bean HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder, TwoFactorService twoFactor) {
        DaoAuthenticationProvider provider = new TwoFactorAuthenticationProvider(twoFactor);
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DeviceSessionRegistry registry, AuthRateLimiter limiter,
            org.thymeleaf.spring6.SpringTemplateEngine templates) throws Exception {
        var responses = new AuthResponses(templates);
        http.addFilterBefore(new AuthRateLimitFilter(limiter, responses),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        var success = new SavedRequestAwareAuthenticationSuccessHandler();
        success.setDefaultTargetUrl("/");
        success.setAlwaysUseDefaultTargetUrl(true);
        var deniedPage = new org.springframework.security.web.access.AccessDeniedHandlerImpl();
        deniedPage.setErrorPage("/access-denied");
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/fonts/**", "/img/**", "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/users/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").authenticationDetailsSource(LoginDetails::new)
                .failureHandler((request, response, failure) -> {
                    if (failure instanceof SecondFactorLockedException locked) {
                        responses.limited(request, response, locked.getRetryAfter());
                    } else {
                        // Never save exception details (which may contain credentials) in the session.
                        response.sendRedirect(request.getContextPath() + "/login?error");
                    }
                })
                .successHandler((request, response, authentication) -> {
                    registry.recordDevice(request.getSession().getId(), request.getHeader("User-Agent"));
                    success.onAuthenticationSuccess(request, response, authentication);
                }).permitAll())
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(registry).expiredUrl("/login?expired"))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> {
                if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"FORBIDDEN\"}");
                } else {
                    deniedPage.handle(request, response, denied);
                }
            }));
        return http.build();
    }
}
