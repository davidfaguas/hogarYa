package cl.hogarya.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Configuración Central de Seguridad Web, CORS y CSRF.
 *
 * TOPOLOGÍA DE DESPLIEGUE Y POLÍTICA SAMESITE DE COOKIES:
 * 1. Topología A (Mismo dominio / Reverse Proxy unificado, ej. hogarya.cl/api y hogarya.cl/):
 *    - Cookie de sesión: SameSite=Strict o Lax, Secure=true, HttpOnly=true.
 *    - Protección nativa contra CSRF y tokens sincronizados para mutaciones.
 * 2. Topología B (Subdominios separados o Cloud Run, ej. api.hogarya.cl y app.hogarya.cl o URL Cloud Run fija):
 *    - CORS estricto con lista explícita de orígenes exactos (sin comodines * en allowCredentials=true).
 *    - Cookie de sesión: SameSite=Lax (subdominios .hogarya.cl) o SameSite=None + Secure (cross-domain seguro).
 *    - Protección CSRF con CookieCsrfTokenRepository (cookie XSRF-TOKEN + cabecera X-XSRF-TOKEN).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${hogarya.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,https://hogarya.cl,https://www.hogarya.cl}") String allowedOriginsStr) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers(
                    // Endpoints públicos o sin sesión previa exentos de CSRF
                    "/api/v1/auth/**",
                    "/api/v1/propiedades/busqueda",
                    "/api/v1/finanzas/**",
                    "/api/v1/simulador",
                    "/api/v1/subsidios/**",
                    "/api/v1/leads",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/**"
                )
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints Públicos de Búsqueda, UF, Subsidios y Simulación
                .requestMatchers(HttpMethod.GET, "/api/v1/propiedades/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/propiedades/busqueda").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/finanzas/uf/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/simulador").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/subsidios/evaluar").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/leads").permitAll()
                // Auth endpoints públicos
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro", "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                // Swagger OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Actuator y salud básica
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Cualquier otra solicitud (ej. GET /api/v1/auth/me, POST /api/v1/propiedades, POST/DELETE /api/v1/favoritos, /actuator/metrics/**) requiere autenticación
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Orígenes exactos por entorno (prohibido uso de comodines '*' cuando allowCredentials es true)
        config.setAllowedOrigins(this.allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("Set-Cookie", "Location", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Filtro para forzar la emisión de la cookie XSRF-TOKEN en respuestas SPA.
     */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Invoca getToken() para forzar la escritura de la cookie en CookieCsrfTokenRepository
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
