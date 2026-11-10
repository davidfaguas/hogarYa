package cl.hogarya.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class JwtTokenService {

    private final byte[] secretKey;
    private final String issuer;
    private final long expirationMinutes;

    public JwtTokenService(
            @Value("${hogarya.security.jwt.secret}") String secret,
            @Value("${hogarya.security.jwt.issuer:https://hogarya.cl}") String issuer,
            @Value("${hogarya.security.jwt.expiration-minutes:60}") long expirationMinutes
    ) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String username, String rut, List<String> roles) {
        try {
            JWSSigner signer = new MACSigner(secretKey);
            Instant now = Instant.now();

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issuer(issuer)
                    .claim("rut", rut)
                    .claim("roles", roles)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Error al firmar token JWT", e);
        }
    }

    public JWTClaimsSet validateAndExtractClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secretKey);

            if (!signedJWT.verify(verifier)) {
                throw new SecurityException("Firma de token JWT inválida");
            }

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new SecurityException("El token JWT ha expirado");
            }

            return signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            throw new SecurityException("Token JWT inválido o adulterado: " + e.getMessage(), e);
        }
    }
}
