package com.agentdoc.auth.security;

import com.agentdoc.auth.enums.LocalClientPurpose;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/** 只由本地机器凭据边界在校验后建立；不保存Secret，也不是人类身份。 */
public final class LocalClientAuthentication extends AbstractAuthenticationToken {
    private final String client;
    private final LocalClientPurpose purpose;

    public LocalClientAuthentication(String client, LocalClientPurpose purpose) {
        super(List.of()); this.client = client; this.purpose = purpose; setAuthenticated(true);
    }
    @Override public Object getPrincipal() { return client; }
    @Override public Object getCredentials() { return ""; }
    @Override public String getName() { return client; }
    public boolean hasPurpose(LocalClientPurpose required) { return purpose == required; }
}
