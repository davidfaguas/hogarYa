package cl.hogarya.domain.service.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Motor HNSW: Verificación de soporte de versión pgvector")
class PropertySearchServiceTest {

    @Test
    @DisplayName("isVersionAtLeast compara semver correctamente")
    void shouldCompareVersionsCorrectly() {
        assertTrue(PropertySearchService.isVersionAtLeast("0.8.0", "0.8.0"));
        assertTrue(PropertySearchService.isVersionAtLeast("0.8.1", "0.8.0"));
        assertTrue(PropertySearchService.isVersionAtLeast("0.9.0", "0.8.0"));
        assertTrue(PropertySearchService.isVersionAtLeast("1.0.0", "0.8.0"));

        assertFalse(PropertySearchService.isVersionAtLeast("0.7.4", "0.8.0"));
        assertFalse(PropertySearchService.isVersionAtLeast("0.7.0", "0.8.0"));
        assertFalse(PropertySearchService.isVersionAtLeast("0.6.2", "0.8.0"));
    }
}
