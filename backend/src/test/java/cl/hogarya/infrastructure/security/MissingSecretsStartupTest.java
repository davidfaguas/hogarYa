package cl.hogarya.infrastructure.security;

import cl.hogarya.HogarYaApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
