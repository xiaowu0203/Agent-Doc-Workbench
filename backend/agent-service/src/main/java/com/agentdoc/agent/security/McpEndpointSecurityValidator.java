package com.agentdoc.agent.security;

import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP外部服务端点安全校验器
 * <p>
 * 用于防范外部MCP服务接入场景下的SSRF攻击，提供两层校验能力：
 * <ol>
 * <li>{@link #validateExternal(String)}：对用户传入原始MCP URL做语法、协议、参数合法性校验；DNS解析域名得到IP列表，校验全部IP为公网非保留地址；</li>
 * <li>{@link #validateResolved(SocketAddress)}：DNS解析完成后，对已经得到的SocketAddress地址实例做运行时二次校验；用于Reactor HttpClient DNS回调链路。</li>
 * </ol>
 * </p>
 * <p>
 * 安全约束规则：
 * <ul>
 * <li>协议强制限定 HTTPS；禁止http、file等其他协议；</li>
 * <li>禁止URI携带 userInfo(账号密码)、query查询参数、fragment片段；</li>
 * <li>端口合法范围：不能为0，不能超过最大TCP端口；</li>
 * <li>DNS解析超时控制，使用虚拟线程执行DNS查询，避免阻塞业务线程；</li>
 * <li>IP黑名单：回环地址、本地链路、内网站点地址、组播、各类IANA保留网段、IPv6文档地址均拦截，只放行公网全局单播IPv4/IPv6；</li>
 * </ul>
 * </p>
 * <p>
 * 使用场景：外部第三方MCP服务由用户输入URL接入时调用；内部可信MCP服务不使用该校验器。
 * 抛出 {@link BusinessException}，错误码为 {@link ErrorCode#VALIDATION_FAILED}。
 * </p>
 */
@Component
public class McpEndpointSecurityValidator {

    /**
     * 校验外部传入MCP原始URL字符串
     * <p>流程：URI语法校验 → 协议、URI组件、端口校验 → DNS域名解析 → 全部解析出来IP做公网地址校验。
     * DNS解析使用虚拟线程执行，携带超时控制；任意一个解析IP属于内网/保留地址直接拒绝。</p>
     * @param value 用户输入MCP服务完整URL
     * @throws BusinessException URL非法、协议不满足、DNS解析超时、解析出内网/保留IP时抛出
     */
    public void validateExternal(String value) {
        try {
            URI uri = URI.create(value);
            // 安全规则：强制HTTPS；host不能为空；禁止userInfo、query、fragment；端口不能为0且不能超出TCP最大端口
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > McpConstant.MAX_TCP_PORT) {
                throw invalid();
            }
            // DNS解析host得到全部IP，每一个IP都必须是公网地址
            for (InetAddress address : resolve(uri.getHost())) {
                requirePublicAddress(address);
            }
        } catch (BusinessException exception) {
            // 业务异常直接透传，不包装
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "外部 MCP 地址无效或无法安全解析");
        }
    }

    /**
     * DNS解析完成后运行时二次校验SocketAddress
     * <p>用于HttpClient dns解析回调链路；DNS拿到SocketAddress之后再次校验IP，防御DNS‑rebinding攻击。</p>
     * @param value DNS解析得到的SocketAddress
     * @throws BusinessException 非InetSocketAddress、地址为空、IP为内网/保留网段抛出
     */
    public void validateResolved(SocketAddress value) {
        if (!(value instanceof InetSocketAddress socketAddress) || socketAddress.getAddress() == null) {
            throw invalid();
        }
        requirePublicAddress(socketAddress.getAddress());
    }

    /**
     * DNS域名解析；使用虚拟线程执行，设置DNS查询超时时间
     * @param host 待解析域名
     * @return 域名对应的全部InetAddress数组
     * @throws Exception 解析超时、线程中断、解析失败抛出，包装为业务异常向上处理
     */
    private InetAddress[] resolve(String host) throws Exception {
        // 启动虚拟线程执行DNS解析，不占用业务线程池
        FutureTask<InetAddress[]> task = new FutureTask<>(() -> InetAddress.getAllByName(host));
        Thread.startVirtualThread(task);
        try {
            return task.get(McpConstant.DNS_RESOLUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "外部 MCP 地址解析超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    /**
     * 校验IP必须为公网地址；拦截各类内网、回环、链路本地、组播、IANA保留地址
     * @param address InetAddress IP实例
     * @throws BusinessException IP属于禁止网段抛出
     */
    private void requirePublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress() || isReserved(address)) {
            throw invalid();
        }
    }

    /**
     * 判断IP是否属于IANA保留网段
     * <p>IPv4：校验0.0.0.0/8、100.64.0.0/10、192.0.0.0/24、192.0.2.0/24、198.18.0.0/15、198.51.100.0/24、203.0.113.0/24、240.0.0.0‑255.255.255.255；
     * IPv6：非全局单播、文档测试地址 2001:db8::/32 返回true。</p>
     * @param address IP地址实例
     * @return true代表为保留网段，需要拦截
     */
    private boolean isReserved(InetAddress address) {
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first == 0
                    || first == 100 && second >= 64 && second <= 127
                    || first == 192 && second == 0 && third == 0
                    || first == 192 && second == 0 && third == 2
                    || first == 198 && (second == 18 || second == 19)
                    || first == 198 && second == 51 && third == 100
                    || first == 203 && second == 0 && third == 113
                    || first >= 240;
        }
        return address instanceof Inet6Address && (!isGlobalUnicastIpv6(address) || isDocumentationIpv6(address));
    }

    /**
     * 判断是否为IPv6全局单播地址（2000::/3）
     * @param address IPv6地址
     * @return true：全局公网单播IPv6
     */
    private boolean isGlobalUnicastIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xe0) == 0x20;
    }

    /**
     * 判断是否为IPv6文档测试地址 2001:0db8::/32，该网段仅用于文档示例，禁止访问
     * @param address IPv6地址
     * @return true：属于文档保留网段
     */
    private boolean isDocumentationIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && Byte.toUnsignedInt(bytes[0]) == 0x20
                && Byte.toUnsignedInt(bytes[1]) == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d
                && Byte.toUnsignedInt(bytes[3]) == 0xb8;
    }

    /**
     * 构造校验失败业务异常
     * @return 携带错误码与提示信息的BusinessException
     */
    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                "外部 MCP 仅允许无用户信息、查询参数和片段的公网 HTTPS 地址");
    }
}
