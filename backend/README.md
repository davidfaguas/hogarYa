# HogarYa - Backend Nuclear (Java 21 + Spring Boot 3 + MyBatis + PostgreSQL 16 / PostGIS / pgvector)

## 1. Subsistemas Críticos Implementados
1. **Motor Geodésico y Semántico Híbrido**:
   - PostgreSQL 16 con extensiones `postgis`, `vector` (pgvector >= 0.7), `pg_trgm`, `unaccent`.
   - Función inmutable `public.immutable_unaccent(text)` para columnas generadas (`search_vector`).
   - Fusión de ranking híbrido mediante **Reciprocal Rank Fusion (RRF)** sobre Top-200 vectorial (HNSW configurado con `SET LOCAL hnsw.ef_search = 200` y escaneo iterativo condicional `iterative_scan = relaxed_order` si `extversion >= 0.8`) y Top-200 textual (`websearch_to_tsquery` + similitud trigram `pg_trgm`).
   - Filtro geodésico exacto a estaciones de Metro de Santiago con `ST_DWithin` en metros con `EXISTS` para evitar multiplicación de filas.
2. **Motor Financiero y Subsidios Chilenos**:
   - Serie histórica de la UF con filtro estricto `fecha <= CURRENT_DATE` evitando lecturas de valores futuros prepublicados.
   - Simulador hipotecario con cálculo continuo en CLP (fórmula francesa) contrastado con tolerancia numérica frente a la función `=PAGO()` de Excel.
   - Manejo protegido de tasa de interés al 0% y cálculo de aranceles según DL 3475, Decreto 588 y garantía estatal FOGAES (Ley Nº 21.543).
   - Servicio de elegibilidad paramétrico para subsidios Minvu DS19 y DS1 (Tramos 1, 2 y 3).
3. **Seguridad, RUT, Outbox y Autenticación**:
   - Validador formal de RUT chileno bajo Módulo 11 con soporte para dígito verificador 'K' (ej. `11111112-K`), '0' (ej. `16243576-0`) y empresas hasta el rango `99.xxx.xxx`.
   - Captación de leads con código correlativo `HY-XXXXX` generado por secuencia PostgreSQL (`seq_lead_tracking_code`).
   - Trazabilidad y hash de consentimiento SHA-256 según Ley 21.719 y Ley 19.628.
   - Patrón **Transactional Outbox** con `@EnableScheduling`, worker concurrente con bloqueo pesimista `SELECT ... FOR UPDATE SKIP LOCKED` y reintentos exponenciales.
   - Seguridad con Spring Security 6, JWT firmado con HMAC-SHA256 emitido en cookie `HttpOnly` (`HOGARYA_SESSION`) mediante `AuthController`.

---

## 2. Instrucciones de Compilación y Ejecución

### Compilación y Empaquetado Ejecutable (Repackage)
El proyecto compila y empaqueta un fat-jar autónomo mediante `spring-boot-maven-plugin:repackage`:
```bash
cd backend
mvn clean package -DskipTests
java -jar target/hogarya-backend-1.0.0-SNAPSHOT.jar
```

### Ejecución de Pruebas Unitarias (0 Tests Omitidos)
Ejecuta la suite unitaria completa sin requerir demonio de Docker:
```bash
cd backend
mvn clean test
```
*Garantía*: 26/26 pruebas unitarias exitosas, **0 fallos, 0 errores, 0 omitidos**.

### Ejecución de Pruebas de Integración con Testcontainers
Los tests de integración (`*IT.java`) levantan el entorno real con `Dockerfile.test-db`:
```bash
# En entorno con Docker (CI o local):
mvn clean verify

# En entorno local sin Docker (omisión explícita documentada):
mvn verify "-Dtest.docker.skip=true"
```
*Regla de Calidad CI*: Si Docker no está activo y no se especifica `-Dtest.docker.skip=true`, las pruebas de integración fallan ruidosamente (`IllegalStateException`).

---

## 3. Política de Cookies y Decisión de Arquitectura SameSite

La cookie de autenticación (`HOGARYA_SESSION`) se configura paramétricamente según la topología de despliegue entre Frontend y Backend:

| Escenario de Despliegue | Topología de Dominios | `SameSite` | `Secure` | Justificación Técnica de Seguridad |
| :--- | :--- | :---: | :---: | :--- |
| **Mismo Dominio (Same-Site / First-Party)** | Frontend: `hogarya.cl`<br>API: `api.hogarya.cl` (mismo eTLD+1) | `Lax` o `Strict` | `true` (HTTPS) | **Máxima protección contra CSRF**. El navegador envía la cookie en peticiones al mismo sitio sin riesgo de falsificación externa. `HttpOnly` previene robo por XSS. |
| **Dominios Distintos (Cross-Site / Third-Party)** | Frontend: `hogarya-app.web.app`<br>API: `hogarya-backend.run.app` | `None` | `true` (Obligatorio) | **Requisito del estándar W3C/IETF**. Si los eTLD difieren, los navegadores modernos descartan silenciosamente cookies con `Lax` o `Strict` en peticiones AJAX/fetch (`credentials: 'include'`). Requiere `SameSite=None` y `Secure=true`. |

### Configuración en `application.yml`:
```yaml
hogarya:
  security:
    cookie:
      secure: ${HOGARYA_COOKIE_SECURE:false}      # true en producción HTTPS
      same-site: ${HOGARYA_COOKIE_SAME_SITE:Lax} # Lax para mismo dominio, None para cross-site
```

---

## 4. Matriz de Defectos y Cobertura

| Defecto | Causa Raíz | Prueba que Falla / Valida | Corrección Implementada |
|---|---|---|---|
| **1. Inmutabilidad unaccent** | `unaccent()` nativo es STABLE (error `42P17`). | `Defects1To3IT.Defecto1UnaccentImmutabilityTest` | `public.immutable_unaccent(text)` con inmutabilidad garantizada. |
| **2. Falta de pgvector** | `postgis/postgis:16-3.4` no incluye extensión `vector`. | `Defects1To3IT.Defecto2PgvectorExtensionTest` | `Dockerfile.test-db` con `postgresql-16-pgvector` instalado. |
| **3. Fecha futura en UF** | `LIMIT 1` tomaba fechas futuras prepublicadas por Banco Central/CMF. | `Defects1To3IT.Defecto3UfDateConditionTest` | Cláusula `WHERE fecha <= CURRENT_DATE` sobre tabla real `valor_uf`. |
| **4. RRF y límite HNSW** | HNSW por defecto explora `ef_search=40`, truncando búsquedas `LIMIT 200`. | `PropertySearchServiceTest` / `PropertySearchMapperIntegrationIT` | `PropertySearchService` ejecuta `SET LOCAL hnsw.ef_search = 200` y evalúa `iterative_scan`. |
| **5. Búsqueda AND rígida** | `plainto_tsquery` no toleraba typos ni aproximaciones. | `PropertySearchMapperIntegrationIT.shouldMatchUsingTextSearchAndTrigram` | `websearch_to_tsquery` + trigramas `pg_trgm`. |
| **6. Duplicación por Metro** | Join 1:N multiplicaba filas por cada estación cercana. | `PropertySearchMapperIntegrationIT.shouldNotDuplicatePropertiesWhenNearMultipleMetroStations` | Filtro espacial `EXISTS` y reutilización de `<sql id="hardFilters">`. |
| **7. Error DV RUT** | DVs falsos en ejemplos y límite <= 50M. | `ChileanRutPropertyBasedTest` | Rango hasta 99.999.999 y validación matemática de casos K y 0 reales. |
| **8. Dividendo continuo** | Redondeos intermedios en UF desfasaban dividendo en CLP. | `ChileanMortgageSimulatorTest` | Cálculo continuo exacto en CLP con `BigDecimal` y comparación con tolerancia. |
| **9. Outbox sin Scheduler** | Falta de `@EnableScheduling` impedía ejecución del worker. | `SchedulingConfig` + `LeadCaptureServiceTest` | `@EnableScheduling` y bloqueo concurrente `FOR UPDATE SKIP LOCKED`. |
| **10. Mapeo MyBatis DTO** | Mapeo reflexivo fallaba con Records inmutables de Java. | `PropertySearchMapperContractTest` | Mapeo explícito vía `<constructor><arg>` en `PropertySearchMapper.xml`. |
