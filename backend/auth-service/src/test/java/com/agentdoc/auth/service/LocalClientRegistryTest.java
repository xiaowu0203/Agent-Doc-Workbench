package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.enums.LocalClientPurpose;
import com.agentdoc.auth.support.LocalIdentityTestInitializer;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalClientRegistryTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void onlyTypedRegisteredClientCanUseItsPurposeAndDisabledSecretCannotAuthenticate() {
        var properties = properties(); var registry = new LocalClientRegistry(properties);
        SecurityContextHolder.getContext().setAuthentication(registry.authenticate("workbench-web", LocalIdentityTestInitializer.BFF_SECRET));
        assertThat(registry.isCurrentBff()).isTrue(); assertThat(registry.isCurrentService()).isFalse();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("workbench-web", "", List.of(new SimpleGrantedAuthority("LOCAL_BFF"))));
        assertThat(registry.isCurrentBff()).isFalse();
        properties.getClients().get("task-service").setEnabled(false);
        assertThatThrownBy(() -> registry.authenticate("task-service", LocalIdentityTestInitializer.SERVICE_SECRET)).isInstanceOf(BusinessException.class);
        assertThat(registry.registeredServices()).contains("task-service");
    }

    @Test
    void unknownAndWrongSecretsHaveTheSameRejection() {
        var registry = new LocalClientRegistry(properties());
        assertThatThrownBy(() -> registry.authenticate("unknown-client", "wrong")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> registry.authenticate("workbench-web", "wrong")).isInstanceOf(BusinessException.class);
    }

    @Test
    void missingDuplicateOrMisclassifiedRegistrationsFailStartupWithoutEchoingSecrets() {
        assertThatThrownBy(() -> new LocalClientRegistry(new LocalIdentityProperties())).isInstanceOf(IllegalStateException.class);
        var duplicate = properties(); duplicate.getClients().get("task-service").setSecret(LocalIdentityTestInitializer.BFF_SECRET);
        assertThatThrownBy(() -> new LocalClientRegistry(duplicate)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(LocalIdentityTestInitializer.BFF_SECRET);
        var misclassified = properties(); misclassified.getClients().get("workbench-web").setPurpose(LocalClientPurpose.SERVICE);
        assertThatThrownBy(() -> new LocalClientRegistry(misclassified)).isInstanceOf(IllegalStateException.class);
    }

    private LocalIdentityProperties properties() {
        var properties = new LocalIdentityProperties();
        var bff = new LocalIdentityProperties.Client(); bff.setPurpose(LocalClientPurpose.BFF); bff.setSecret(LocalIdentityTestInitializer.BFF_SECRET);
        var service = new LocalIdentityProperties.Client(); service.setPurpose(LocalClientPurpose.SERVICE); service.setSecret(LocalIdentityTestInitializer.SERVICE_SECRET);
        properties.getClients().put("workbench-web", bff); properties.getClients().put("task-service", service); return properties;
    }
}
