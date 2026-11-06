package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityKeyUtilsTest {
    @Test
    void fixedVectorsPreserveUtf8BytesAndExactIssuer() {
        assertThat(HexFormat.of().formatHex(IdentityKeyUtils.identityKey("https://id.example/realms/demo", "550e8400-e29b-41d4-a716-446655440000")))
                .isEqualTo("cf7389038d7ac7042f5a2dd17732f05452276de5f4438af5d1269d1de83c8262");
        assertThat(HexFormat.of().formatHex(IdentityKeyUtils.identityKey("https://身份.example", "用户😀")))
                .isEqualTo("1cf8ce8ce9224fd7eefdf5a1277aefb82b24c52b2ebcfe8fa4dffc6d36238bfe");
        assertThat(IdentityKeyUtils.identityKey("https://id.example", "x"))
                .isNotEqualTo(IdentityKeyUtils.identityKey("https://id.example/", "x"));
        assertThat(IdentityKeyUtils.identityKey("ab", "c")).isNotEqualTo(IdentityKeyUtils.identityKey("a", "bc"));
    }

    @Test
    void rejectsMissingOrMalformedIdentity() {
        assertThatThrownBy(() -> IdentityKeyUtils.identityKey(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentityKeyUtils.identityKey("x", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentityKeyUtils.identityKey("x", "\uD800")).isInstanceOf(IllegalArgumentException.class);
    }
}
