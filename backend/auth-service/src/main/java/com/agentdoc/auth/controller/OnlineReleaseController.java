package com.agentdoc.auth.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.security.OnlineReleaseAuthorization;
import com.agentdoc.common.utils.OnlineReleaseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OnlineReleaseController {
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;
    @GetMapping("/api/auth/internal/online-release")
    public Result<String> release(@RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String control) {
        OnlineReleaseAuthorization.require(control, verifiers.getIfAvailable()); return Result.ok(OnlineReleaseUtils.current());
    }
}
