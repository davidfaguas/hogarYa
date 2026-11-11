package cl.hogarya.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityConfigTest: Validación unitaria de CORS exacto por entorno y políticas CSRF")
class SecurityConfigTest {

    @Test
    @DisplayName("CORS: Orígenes exactos configurados son autorizados sin comodines")
    void shouldValidateExactCorsOriginsWithoutWildcards() {
        String exactOrigins = "http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,https://hogarya.cl,https://www.hogarya.cl";
        SecurityConfig securityConfig = new SecurityConfig(null, exactOrigins);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/propiedades");
        request.addHeader("Origin", "http://localhost:3000");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config, "CorsConfiguration debe estar presente para la ruta solicitada");

        // 1. Orígenes permitidos exactos
        assertEquals("http://localhost:3000", config.checkOrigin("http://localhost:3000"));
        assertEquals("http://localhost:5173", config.checkOrigin("http://localhost:5173"));
        assertEquals("https://hogarya.cl", config.checkOrigin("https://hogarya.cl"));
        assertEquals("https://www.hogarya.cl", config.checkOrigin("https://www.hogarya.cl"));

        // 2. Orígenes no autorizados o maliciosos son rechazados
        assertNull(config.checkOrigin("http://evil-attacker.com"));
        assertNull(config.checkOrigin("https://malicious.com"));
        assertNull(config.checkOrigin("https://attacker.run.app"));
        assertNull(config.checkOrigin("https://fake-hogarya.cl"));

        // 3. Verificación estricta: NO se permiten comodines '*' cuando allowCredentials=true
        assertTrue(config.getAllowCredentials(), "AllowCredentials debe ser true para sesiones seguras");
        assertTrue(config.getAllowedOrigins().stream().noneMatch(o -> o.contains("*")),
                "Ningún origen permitido debe contener el carácter comodín '*'");

        // 4. Cabeceras permitidas y expuestas
        assertTrue(config.getAllowedHeaders().contains("X-XSRF-TOKEN"), "Debe permitir cabecera X-XSRF-TOKEN para CSRF");
        assertTrue(config.getExposedHeaders().contains("Set-Cookie"), "Debe exponer cabecera Set-Cookie");
        assertTrue(config.getExposedHeaders().contains("X-XSRF-TOKEN"), "Debe exponer cabecera X-XSRF-TOKEN");
    }

    @Test
    @DisplayName("CORS: Configuración personalizada por variable de entorno sin comodines")
    void shouldSupportCustomExactOriginsFromEnvironment() {
        String envOrigins = "https://ais-dev-preview-123.run.app,https://custom-deploy.hogarya.cl";
        SecurityConfig securityConfig = new SecurityConfig(null, envOrigins);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/propiedades");
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        assertEquals("https://ais-dev-preview-123.run.app", config.checkOrigin("https://ais-dev-preview-123.run.app"));
        assertEquals("https://custom-deploy.hogarya.cl", config.checkOrigin("https://custom-deploy.hogarya.cl"));
        assertNull(config.checkOrigin("https://ais-dev-otro.run.app"));
    }
}
