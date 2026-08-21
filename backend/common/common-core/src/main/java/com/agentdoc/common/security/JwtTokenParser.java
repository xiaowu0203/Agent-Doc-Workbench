package com.agentdoc.common.security;

import com.agentdoc.common.context.LoginUser;
import io.micrometer.common.util.StringUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * JWT 解析工具：将（已校验通过的）Jwt 映射为 {@link LoginUser}，供网关与业务服务复用。
 * <p>
 * 注意：本工具<strong>不做签名校验</strong>，签名、过期等合法性校验由传入的 {@link JwtDecoder} 完成；
 * 调用方必须传入已经校验完毕的 Jwt 对象再调用 {@link #toLoginUser(Jwt)}。
 * </p>
 * <p>
 * JWT Payload 字段约定：
 * <ul>
 * <li>sub：用户ID（人类用户）</li>
 * <li>username：用户名</li>
 * <li>nickname：用户昵称，缺失时回退使用 username</li>
 * <li>scope：权限作用域，多个作用域使用英文逗号分隔</li>
 * <li>agentId：Agent调用标识，Agent场景下存在，普通用户不存在该claim</li>
 * </ul>
 * </p>
 */
public class JwtTokenParser {

    /**
     * 使用给定解码器解析并校验 JWT token。
     * <p>会自动校验签名、过期时间等；校验失败抛出对应Jwt异常。</p>
     * @param token JWT字符串
     * @param decoder Spring Security JWT解码器，完成签名与时效校验
     * @return 校验通过后的 {@link Jwt} 对象
     */
    public Jwt decode(String token, JwtDecoder decoder) {
        return decoder.decode(token);
    }

    /**
     * 将<strong>已经校验完成</strong>的Jwt声明转换为登录主体 {@link LoginUser}。
     * <p>约定声明：sub=用户 ID、username、nickname、scope（逗号分隔）、agentId（Agent 访问时）。</p>
     * @param jwt 已完成签名、过期校验的Jwt实例
     * @return 组装完成的 LoginUser 登录主体
     * @throws NumberFormatException sub / agentId 字段格式非数字时抛出
     */
    public LoginUser toLoginUser(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        String username = jwt.getClaimAsString("username");
        String nickname = jwt.getClaimAsString("nickname");
        // 昵称为空时降级使用用户名
        if (nickname == null || nickname.isBlank()) {
            nickname = username;
        }
        Long agentId = null;
        if (jwt.hasClaim("agentId")) {
            Object value = jwt.getClaim("agentId");
            agentId = value == null ? null : Long.valueOf(value.toString());
        }
        return new LoginUser(userId, username, nickname, agentId, splitScopes(jwt.getClaimAsString("scope")));
    }

    /**
     * 分割 scope 逗号字符串，转为小写的权限集合，自动过滤空值、去除前后空格。
     * @param value jwt中scope原始字符串，逗号分隔
     * @return 去重、小写化的scope集合；输入null/空白返回空集合
     */
    private Set<String> splitScopes(String value) {
        if (StringUtils.isBlank(value)) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
