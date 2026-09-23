package com.marketgrid.vendor;

import com.marketgrid.common.JwtFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration public class SecurityConfig {
  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable);
    http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    http.addFilterBefore(new JwtFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
