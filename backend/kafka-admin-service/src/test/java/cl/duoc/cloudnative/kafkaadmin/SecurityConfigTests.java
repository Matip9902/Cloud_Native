package cl.duoc.cloudnative.kafkaadmin;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class SecurityConfigTests {
    private static final String TENANT = "94b15b07-9bad-4661-8554-aad0e62b18de";
    private static final String V2 = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
    private static final String V1 = "https://sts.windows.net/" + TENANT + "/";
    private static final String AUDIENCE = "api://0f18b5b0-285b-4050-a959-245884ab7670";
    private static RSAKey signingKey;
    private static HttpServer server;
    private static String jwks;
    private JwtDecoder decoder;

    @BeforeAll
    static void keys() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("local-regression-key").generate();
        byte[] publicKeys = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, publicKeys.length);
            try (var output = exchange.getResponseBody()) { output.write(publicKeys); }
        });
        server.start();
        jwks = "http://127.0.0.1:" + server.getAddress().getPort() + "/keys";
    }
    @AfterAll static void stop() { if (server != null) server.stop(0); }
    @BeforeEach void decoder() { decoder = new SecurityConfig().decoder(V2, jwks, AUDIENCE); }

    private String token(String issuer, String audience, Instant expiry, RSAKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("test-user")
                .issueTime(Date.from(Instant.now().minusSeconds(7200))).expirationTime(Date.from(expiry))
                .claim("scp", "inventory.read inventory.write").build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
                .keyID("local-regression-key").build(), claims);
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }
    @Test void acceptsConfiguredTenantV2() throws Exception {
        assertThat(decoder.decode(token(V2, AUDIENCE, Instant.now().plusSeconds(600), signingKey)).getSubject()).isEqualTo("test-user");
    }
    @Test void acceptsConfiguredTenantV1LikeExistingServices() throws Exception {
        assertThat(decoder.decode(token(V1, AUDIENCE, Instant.now().plusSeconds(600), signingKey)).getSubject()).isEqualTo("test-user");
    }
    @Test void acceptsV2WhenConfiguredWithSameTenantV1() throws Exception {
        JwtDecoder v1Configured = new SecurityConfig().decoder(V1, jwks, AUDIENCE);
        assertThat(v1Configured.decode(token(V2, AUDIENCE, Instant.now().plusSeconds(600), signingKey)).getSubject()).isEqualTo("test-user");
    }
    @Test void rejectsMissingIssuer() throws Exception {
        String token = token(null, AUDIENCE, Instant.now().plusSeconds(600), signingKey);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsAnotherTenant() throws Exception {
        String token = token("https://sts.windows.net/00000000-0000-0000-0000-000000000000/", AUDIENCE, Instant.now().plusSeconds(600), signingKey);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsIncorrectAudience() throws Exception {
        String token = token(V2, "api://another-api", Instant.now().plusSeconds(600), signingKey);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsExpiredToken() throws Exception {
        String token = token(V2, AUDIENCE, Instant.now().minusSeconds(3600), signingKey);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsForgedSignature() throws Exception {
        RSAKey untrusted = new RSAKeyGenerator(2048).keyID("local-regression-key").generate();
        String token = token(V2, AUDIENCE, Instant.now().plusSeconds(600), untrusted);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsMicrosoftIssuerForDifferentTenantV2() throws Exception {
        String token = token("https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0", AUDIENCE, Instant.now().plusSeconds(600), signingKey);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
