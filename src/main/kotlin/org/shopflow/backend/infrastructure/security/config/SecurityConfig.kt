package org.shopflow.backend.infrastructure.security.config

import jakarta.servlet.DispatcherType
import org.shopflow.backend.infrastructure.security.jwt.JwtAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
    private val accessDeniedHandler: AccessDeniedHandler,
    @Value("\${app.cors.allowed-origins}") private val allowedOriginsRaw: String,
) {
    @Bean
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain =
        httpSecurity
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/", "/api/status")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**", "/api/products", "/api/products/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                    ).permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .authenticated()
                    .dispatcherTypeMatchers(
                        DispatcherType.ERROR,
                        DispatcherType.FORWARD,
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling { configurer ->
                configurer.authenticationEntryPoint(authenticationEntryPoint)
                configurer.accessDeniedHandler(accessDeniedHandler)
            }.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val allowedOrigins =
            allowedOriginsRaw
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        val config =
            CorsConfiguration().apply {
                this.allowedOrigins = allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type")
                allowCredentials = true
            }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
