package com.agentdoc.auth.support;

import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.JwtService;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

/** 只生成已退役协议的负例；正式签发器不得为测试保留旧入口。 */
public final class LegacyHumanCredentialFixture {
    private LegacyHumanCredentialFixture() { }

    public static String issue(JwtService capabilities, UserEntity user) {
        var encoder = (JwtEncoder) ReflectionTestUtils.getField(capabilities, "encoder");
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(capabilities.props().issuer()).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("scope", "user").claim("platformRoles", List.of()).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
