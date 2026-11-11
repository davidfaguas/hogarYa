package cl.hogarya.infrastructure.security;

import cl.hogarya.HogarYaApplication;
import cl.hogarya.domain.service.search.EmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Seguridad: Falla obligatoria al arrancar fuera de dev/test sin secretos")
class MissingSecretsStartupTest {

    @Test
    @DisplayName("La aplicación falla al arrancar en perfil prod/default si falta HOGARYA_JWT_SECRET o contraseña de BD")
    void shouldFailToStartWithoutRequiredSecretsInProduction() {
        assertThrows(Exception.class, () -> {
            new SpringApplicationBuilder(HogarYaApplication.class)
                    .profiles("prod")
                    .run("--server.port=0");
        }, "Debe arrojar excepción al no poder resolver las propiedades obligatorias");
    }

    @Test
    @DisplayName("Falla al arrancar en perfil producción si no existe una implementación real de EmbeddingProvider")
    void shouldFailToStartInProductionWithoutRealEmbeddingProvider() {
        Exception ex = assertThrows(Exception.class, () -> {
            new SpringApplicationBuilder(HogarYaApplication.class)
                    .profiles("prod")
                    .run(
                            "--server.port=0",
                            "--spring.datasource.password=test_secret",
                            "--spring.flyway.enabled=false",
                            "--hogarya.security.jwt.secret=ClaveSecretaHogarYaChileSuperSegura2026DeAlMenos256BitsLongitudMinima"
                    );
        });

        Throwable current = ex;
        boolean foundExplicitMessage = false;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("No se ha configurado un proveedor real de EmbeddingProvider")) {
                foundExplicitMessage = true;
                break;
            }
            current = current.getCause();
        }
        assertTrue(foundExplicitMessage, "Debe contener el mensaje claro que explica la falta de EmbeddingProvider en prod");
    }

    @Test
    @DisplayName("Falla al arrancar en perfil producción si HOGARYA_TRUSTED_PROXIES no está definida")
    void shouldFailToStartInProductionWithoutTrustedProxiesConfigured() {
        EmbeddingProvider dummyProvider = new EmbeddingProvider() {
            @Override
            public float[] generateEmbedding(String text) {
                return new float[768];
            }
            @Override
            public int getDimensions() {
                return 768;
            }
        };

        Exception ex = assertThrows(Exception.class, () -> {
            new SpringApplicationBuilder(HogarYaApplication.class)
                    .profiles("prod")
                    .initializers((GenericApplicationContext ctx) -> ctx.registerBean(EmbeddingProvider.class, () -> dummyProvider))
                    .run(
                            "--server.port=0",
                            "--spring.datasource.password=test_secret",
                            "--spring.flyway.enabled=false",
                            "--hogarya.security.jwt.secret=ClaveSecretaHogarYaChileSuperSegura2026DeAlMenos256BitsLongitudMinima"
                            // HOGARYA_TRUSTED_PROXIES no se pasa intencionalmente
                    );
        });

        Throwable current = ex;
        boolean foundExplicitMessage = false;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("La variable de entorno HOGARYA_TRUSTED_PROXIES es obligatoria")) {
                foundExplicitMessage = true;
                break;
            }
            current = current.getCause();
        }
        assertTrue(foundExplicitMessage, "Debe contener el mensaje claro indicando que HOGARYA_TRUSTED_PROXIES es obligatoria en prod");
    }
}
