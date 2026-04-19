package com.mudhut.software.kasisira.config

import com.mudhut.software.kasisira.security.CustomUserDetailsService
import com.mudhut.software.kasisira.security.JwtAuthenticationFilter
import com.mudhut.software.kasisira.security.OAuth2AuthenticationSuccessHandler
import com.mudhut.software.kasisira.security.RestAccessDeniedHandler
import com.mudhut.software.kasisira.security.RestAuthenticationEntryPoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.http.HttpMethod
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig {

    @Autowired
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @Autowired
    private lateinit var oauth2AuthenticationSuccessHandler: OAuth2AuthenticationSuccessHandler

    @Autowired
    private lateinit var restAuthenticationEntryPoint: RestAuthenticationEntryPoint

    @Autowired
    private lateinit var restAccessDeniedHandler: RestAccessDeniedHandler

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val authProvider = DaoAuthenticationProvider(customUserDetailsService)
        authProvider.setPasswordEncoder(passwordEncoder())
        return authProvider
    }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.exposedHeaders = listOf("Authorization")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(restAuthenticationEntryPoint)
                it.accessDeniedHandler(restAccessDeniedHandler)
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    // Public endpoints (paths are relative to context-path, so exclude /api prefix)
                    .requestMatchers(
                        "/v1/auth/register",
                        "/v1/auth/login",
                        "/v1/auth/refresh",
                        "/v1/auth/verify-email",
                        "/v1/auth/resend-verification",
                        "/v1/auth/check-email-verified",
                        "/v1/auth/phone/**",
                        "/oauth2/**",
                        "/error",
                        "/actuator/health"
                    ).permitAll()
                    // Public property endpoints (GET only for browsing)
                    .requestMatchers(HttpMethod.GET, "/v1/properties").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/search").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/type/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/listing/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/{propertyId}/media").permitAll()
                    // All other requests require authentication
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .successHandler(oauth2AuthenticationSuccessHandler)
            }
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
