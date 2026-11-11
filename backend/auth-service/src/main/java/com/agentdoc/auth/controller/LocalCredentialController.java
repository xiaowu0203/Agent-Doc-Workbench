package com.agentdoc.auth.controller;

import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.service.LocalCredentialService;
import com.agentdoc.auth.service.LocalIdentityJwtService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.LocalHumanCredentialsDTO;
import com.agentdoc.common.feign.dto.ServiceProofRequestDTO;
import com.agentdoc.common.feign.vo.LocalHumanCredentialVO;
import com.agentdoc.common.feign.vo.ServiceProofVO;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 受信服务端协议，不是浏览器Token登录接口。 */
@Hidden
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalCredentialController {
    private final LocalCredentialService credentials;
    private final LocalIdentityJwtService signer;

    @PostMapping(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH)
    public Result<LocalHumanCredentialVO> human(@RequestBody LocalHumanCredentialsDTO request) { return Result.ok(credentials.issueHuman(request)); }
    @PostMapping(LocalIdentityConstant.SERVICE_PROOF_PATH)
    public Result<ServiceProofVO> service(@RequestBody ServiceProofRequestDTO request) { return Result.ok(credentials.issueService(request)); }
    @GetMapping("/oauth2/identity-jwks")
    public Map<String, Object> jwks() { return signer.publicJwks(); }
}
