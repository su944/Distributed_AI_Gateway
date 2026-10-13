package com.sunny.gateway.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticator authenticator, SecurityErrors errors) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, exception) -> errors.write(response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> errors.write(response, HttpStatus.FORBIDDEN)))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/chat/completions").hasAuthority(Permission.CHAT_COMPLETIONS.name())
                        .anyRequest().denyAll())
                .addFilterBefore(new ApiKeyFilter(authenticator, errors), AnonymousAuthenticationFilter.class)
                .build();
    }
}
