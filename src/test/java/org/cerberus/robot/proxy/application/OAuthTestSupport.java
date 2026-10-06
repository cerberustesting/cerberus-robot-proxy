package org.cerberus.robot.proxy.application;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** Mints JWTs signed with a throw-away key and exposes the matching decoder (no Keycloak needed). */
final class OAuthTestSupport {

    static final String ISSUER = "http://keycloak.invalid/realms/cerberus";
    private static final RSAKey KEY;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private OAuthTestSupport() {
    }

    /**
     * Deliberately NOT annotated (@TestConfiguration/@Configuration): Application's explicit @ComponentScan
     * would pick it up in every test context. Pulled in with @Import by the OAuth tests only.
     */
    static class DecoderConfig {

        /** Replaces the issuer-based decoder of Spring Boot, which would call the (non-existing) Keycloak. */
        @Bean
        JwtDecoder jwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        }
    }

    static String token(long ttlSeconds) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("cerberus")
                .expirationTime(new Date(System.currentTimeMillis() + ttlSeconds * 1000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    static ResponseEntity<String> get(TestRestTemplate rest, String path, String bearer) {
        HttpHeaders h = new HttpHeaders();
        if (bearer != null) {
            h.set("Authorization", "Bearer " + bearer);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }
}
