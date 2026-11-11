package com.agentdoc.auth.config;

import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.enums.LocalClientPurpose;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** 本地身份运行配置；限流阈值及期限是已冻结协议，不从部署配置放宽。 */
@Data
@ConfigurationProperties(prefix = "auth.identity")
public class LocalIdentityProperties {
    /** 共享登录限流Redis键空间；仅隔离环境使用不同前缀。 */
    private String loginRateNamespace = LocalIdentityConstant.LOGIN_RATE_NAMESPACE;

    /** 独立身份RSA私钥PKCS8 PEM，必须从受管环境提供。 */
    @ToString.Exclude
    private String privateKey = "";

    /** 与身份私钥匹配的RSA公钥X509 PEM。 */
    private String publicKey = "";
    /** TLS在等价加密的受控内部网络终止时显式启用；默认只允许TLS/loopback。 */
    private boolean trustedEncryptedNetwork;

    /** 显式登记的独立机器Secret；不提供公共默认Secret。 */
    private Map<String, Client> clients = new LinkedHashMap<>();

    /** 单个调用方的机器认证配置。 */
    @Data
    public static class Client {
        /** 唯一用途；workbench-web只能为BFF，其他服务只能为SERVICE。 */
        private LocalClientPurpose purpose;
        /** 独立受管机器Secret，UTF8长度32～512字节，不能在调用方间复用。 */
        @ToString.Exclude
        private String secret = "";
        /** 停用后拒绝新的签发请求；不伪称立即撤销已签JWT。 */
        private boolean enabled = true;
    }
}
