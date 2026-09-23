package com.marketgrid.user;

import com.marketgrid.common.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @EnableWebSecurity
public class SecurityConfig {
  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/**", "/actuator/health").permitAll().anyRequest().authenticated()).addFilterBefore(new JwtFilter(), UsernamePasswordAuthenticationFilter.class).build(); }
}
