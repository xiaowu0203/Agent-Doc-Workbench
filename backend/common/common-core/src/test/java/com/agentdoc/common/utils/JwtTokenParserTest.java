package com.agentdoc.common.utils;

import com.agentdoc.common.context.LoginUser;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenParserTest {

    private final JwtTokenParser parser = new JwtTokenParser();

    @Test
    void mapsJwtClaimsToLoginUser() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("42")
                .claim("username", "alice")
                .claim("nickname", "Alice")
                .claim("scope", "user, doc:read")
                .build();

        LoginUser user = parser.toLoginUser(jwt);

        assertEquals(42L, user.userId());
        assertEquals("alice", user.username());
        assertEquals("Alice", user.nickname());
        assertFalse(user.isAgent());
        assertTrue(user.scopes().contains("user"));
        assertTrue(user.scopes().contains("doc:read"));
    }

    @Test
    void nicknameFallsBackToUsernameAndScopesEmpty() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("7")
                .claim("username", "bob")
                .build();

        LoginUser user = parser.toLoginUser(jwt);

        assertEquals("bob", user.nickname());
        assertTrue(user.scopes().isEmpty());
        assertFalse(user.isAgent());
    }

    @Test
    void decodeRoundTripWithRs256() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("7")
                .claim("username", "bob")
                .claim("scope", "user")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        Jwt jwt = parser.decode(token, decoder);

        assertEquals("7", jwt.getSubject());
        assertEquals("bob", jwt.getClaimAsString("username"));
    }
}
