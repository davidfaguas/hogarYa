package cl.hogarya.domain.model.identity;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object inmutable para el Rol Único Tributario (RUT / RUN) de Chile.
 * Corrección Defecto 7:
 * - Soporta personas naturales y empresas (rango 1.000.000 a 99.999.999).
 * - Algoritmo formal Módulo 11 con dígito verificador 'K' (ej: 11111112-K) y '0' (ej: 16243576-0).
 */
public final class ChileanRut implements Serializable {

    private static final Pattern CLEAN_PATTERN = Pattern.compile("^[0-9]{7,8}-[0-9K]$");
    private final int numero;
    private final char dv;

    private ChileanRut(int numero, char dv) {
        this.numero = numero;
        this.dv = dv;
    }

    public static ChileanRut of(String rawRut) {
        if (rawRut == null) {
            throw new IllegalArgumentException("El RUT no puede ser nulo");
        }
        String clean = rawRut.replace(".", "").replace(" ", "").trim().toUpperCase();
        if (!CLEAN_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Formato de RUT inválido. Debe ser NNNNNNNN-D (ej: 11111112-K)");
        }
        String[] parts = clean.split("-");
        int num = Integer.parseInt(parts[0]);
        char dv = parts[1].charAt(0);

        // Rango válido civil y comercial en Chile: desde 1 millón hasta 99 millones (personas jurídicas / empresas)
        if (num < 1_000_000 || num > 99_999_999) {
            throw new IllegalArgumentException("Cuerpo de RUT fuera de rango civil o tributario chileno (1.000.000 a 99.999.999)");
        }

        char expectedDv = calculateDv(num);
        if (expectedDv != dv) {
            throw new IllegalArgumentException(String.format(
                    "Dígito verificador inválido para el cuerpo %d: provisto '%c', esperado '%c'", 
                    num, dv, expectedDv));
        }
        return new ChileanRut(num, dv);
    }

    public static boolean isValid(String rawRut) {
        try {
            of(rawRut);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Algoritmo formal de Módulo 11 chileno.
     */
    public static char calculateDv(int rut) {
        int m = 0, s = 1;
        for (int t = rut; t != 0; t /= 10) {
            s = (s + t % 10 * (9 - m++ % 6)) % 11;
        }
        return (char) (s != 0 ? s + 47 : 75); // 75 en ASCII = 'K'
    }

    public String toCanonicalString() {
        return numero + "-" + dv;
    }

    public int getNumero() { return numero; }
    public char getDv() { return dv; }
    public int cuerpo() { return numero; }
    public char dv() { return dv; }
    public String formatoConPuntosYGuion() {
        return String.format(java.util.Locale.GERMANY, "%,d-%c", numero, dv);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChileanRut that)) return false;
        return numero == that.numero && dv == that.dv;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numero, dv);
    }

    @Override
    public String toString() {
        return toCanonicalString();
    }
}
