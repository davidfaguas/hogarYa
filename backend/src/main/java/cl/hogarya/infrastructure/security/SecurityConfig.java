package cl.hogarya.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

/**
 * Configuración Central de Seguridad Web y CORS.
 *
 * TOPOLOGÍA DE DESPLIEGUE Y POLÍTICA SAMESITE DE COOKIES:
 * 1. Topología A (Mismo dominio / Reverse Proxy unificado, ej. hogarya.cl/api y hogarya.cl/):
 *    - Cookie de sesión: SameSite=Strict o Lax, Secure=true, HttpOnly=true.
 *    - No requiere cabeceras CORS cross-origin complejas; protección nativa contra CSRF.
 * 2. Topología B (Subdominios separados o Cloud Run, ej. api.hogarya.cl y app.hogarya.cl o *.run.app):
 *    - CORS explícito con AllowCredentials=true y AllowedOriginPatterns validados.
 *    - ExposedHeaders: Set-Cookie, Location.
 *    - Cookie de sesión: SameSite=Lax (para subdominios bajo .hogarya.cl) o SameSite=None + Secure (para dominios distintos).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints Públicos de Búsqueda, UF, Subsidios y Simulación
                .requestMatchers(HttpMethod.GET, "/api/v1/propiedades/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/propiedades/busqueda").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/finanzas/uf/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/finanzas/simular-credito", "/api/v1/simulador").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/subsidios/evaluar").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/leads").permitAll()
                // Auth endpoints públicos
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro", "/api/v1/auth/logout").permitAll()
                // Swagger OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Actuator y salud
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Cualquier otra solicitud (ej. /api/v1/favoritos) requiere autenticación
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Dominios autorizados del Frontend (Cloud Run dev/pre y local)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "https://ais-dev-*.run.app",
                "https://ais-pre-*.run.app",
                "https://hogarya.cl",
                "https://*.hogarya.cl"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Set-Cookie", "Location"));
        config.setAllowCredentials(true); // Requerido para cookies HttpOnly de sesión
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
