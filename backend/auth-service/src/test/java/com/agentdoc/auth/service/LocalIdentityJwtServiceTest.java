package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.support.LocalIdentityTestInitializer;
import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.utils.JsonUtils;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalIdentityJwtServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void realHumanSignatureHasFixedDeadlineVersionSessionAndNoPrivateJwk() throws Exception {
        var signer = signer(Clock.fixed(NOW, ZoneOffset.UTC));
        var user = new UserEntity(); user.setId(42L); user.setStatus(1); user.setAuthVersion(3L);
        var issued = signer.issueHuman(user);
        var jwt = signer.humanDecoder().decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(IdentityTokenConstant.LOCAL_ISSUER);
        assertThat(jwt.getClaims().get("auth_version").toString()).isEqualTo("3");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(issued.sessionId());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(604800));
        assertThat(issued.toString()).doesNotContain(issued.value());
        var keys = JWKSet.parse(JsonUtils.toJson(signer.publicJwks())).getKeys();
        assertThat(keys.size()).isEqualTo(1); assertThat(keys.getFirst().isPrivate()).isFalse();
        assertThat(keys.getFirst().getKeyID()).startsWith(IdentityTokenConstant.LOCAL_KEY_PREFIX);
    }

    @Test
    void realServiceProofCannotBeUsedAsHumanOrByAnotherClient() {
        var signer = signer(Clock.fixed(NOW, ZoneOffset.UTC));
        var issued = signer.issueService("task-service");
        var jwt = signer.serviceDecoder(Set.of("task-service")).decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("task-service");
        assertThat(jwt.getClaims()).doesNotContainKeys("auth_version", "sid", "platformRoles", "userId");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> signer.humanDecoder().decode(issued.value())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> signer.serviceDecoder(Set.of("other-service")).decode(issued.value())).isInstanceOf(JwtException.class);
    }

    @Test
    void existingProofHasAtMostNinetySecondResidualAcceptance() {
        Clock clock = mock(Clock.class); when(clock.instant()).thenReturn(NOW);
        var signer = signer(clock); var issued = signer.issueService("task-service");
        when(clock.instant()).thenReturn(NOW.plusSeconds(90));
        assertThat(signer.serviceDecoder(Set.of("task-service")).decode(issued.value()).getSubject()).isEqualTo("task-service");
        when(clock.instant()).thenReturn(NOW.plusSeconds(91));
        assertThatThrownBy(() -> signer.serviceDecoder(Set.of("task-service")).decode(issued.value())).isInstanceOf(JwtException.class);
    }

    @Test
    void sameManagedIdentityKeyHasStableKidAcrossServiceReconstruction() throws Exception {
        var first = JWKSet.parse(JsonUtils.toJson(signer(Clock.fixed(NOW, ZoneOffset.UTC)).publicJwks())).getKeys().getFirst();
        var second = JWKSet.parse(JsonUtils.toJson(signer(Clock.fixed(NOW, ZoneOffset.UTC)).publicJwks())).getKeys().getFirst();
        assertThat(first.getKeyID()).isEqualTo(second.getKeyID());
    }

    @Test
    void absentMismatchedOrCapabilityKeysCannotEnableTheIssuer() {
        var capabilities = capabilities();
        assertThatThrownBy(() -> new LocalIdentityJwtService(new LocalIdentityProperties(), capabilities, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        var properties = properties();
        properties.setPublicKey(LocalIdentityTestInitializer.pem("PUBLIC KEY", LocalIdentityTestInitializer.CAPABILITY_KEYS.getPublic().getEncoded()));
        assertThatThrownBy(() -> new LocalIdentityJwtService(properties, capabilities, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        when(capabilities.getPublicKey()).thenReturn((RSAPublicKey) LocalIdentityTestInitializer.IDENTITY_KEYS.getPublic());
        assertThatThrownBy(() -> new LocalIdentityJwtService(properties(), capabilities, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }

    private LocalIdentityJwtService signer(Clock clock) { return new LocalIdentityJwtService(properties(), capabilities(), clock); }
    private JwtService capabilities() {
        JwtService capabilities = mock(JwtService.class);
        when(capabilities.getPublicKey()).thenReturn((RSAPublicKey) LocalIdentityTestInitializer.CAPABILITY_KEYS.getPublic());
        return capabilities;
    }
    private LocalIdentityProperties properties() {
        var properties = new LocalIdentityProperties();
        properties.setPrivateKey(LocalIdentityTestInitializer.pem("PRIVATE KEY", LocalIdentityTestInitializer.IDENTITY_KEYS.getPrivate().getEncoded()));
        properties.setPublicKey(LocalIdentityTestInitializer.pem("PUBLIC KEY", LocalIdentityTestInitializer.IDENTITY_KEYS.getPublic().getEncoded()));
        return properties;
    }
}
