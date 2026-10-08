package org.example.karvachauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * TESTING ONLY — true opens /api/dashboard/** without login so the dashboard APIs can be checked
     * from the browser / Postman. Default false. Set it ONLY in your local properties, never in production.
     * Remove this once the dashboard JWT login is added.
     */
    @Value("${karvachauth.dashboard.open-for-testing:false}")
    private boolean dashboardOpenForTesting;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/webhook/karvachauth", "/webhook/karvachauth/**", "/error").permitAll();
                    if (dashboardOpenForTesting) {
                        auth.requestMatchers("/api/dashboard/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }
}