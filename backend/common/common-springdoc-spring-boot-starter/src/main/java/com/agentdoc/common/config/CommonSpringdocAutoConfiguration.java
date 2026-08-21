package com.agentdoc.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SpringDoc OpenAPI 自动配置，归属 common‑springdoc‑spring‑boot‑starter。
 * <p>
 * 能力：
 * <ul>
 * <li>提供统一默认 OpenAPI 文档模板：标题、描述、版本支持配置文件自定义；</li>
 * <li>内置 bearerAuth JWT 安全认证方案，Swagger‑UI 支持输入 Bearer Token；</li>
 * <li>业务服务无需额外配置，自动提供 {@code /v3/api‑docs} 接口与 {@code /swagger‑ui.html} 文档页面；</li>
 * <li>业务服务可自行声明 {@link OpenAPI} Bean，覆盖本默认实例（{@link ConditionalOnMissingBean}）。</li>
 * </ul>
 * <p>生效条件：classpath 存在 OpenAPI 类（引入 springdoc‑openapi‑starter‑mvc / webflux）才会加载本配置。
 * <p>安全说明：全局添加 {@link SecurityRequirement}，所有接口默认带上JWT认证入口；
 * 匿名接口仍可正常访问，仅文档UI展示Token输入框，不会强制拦截HTTP请求。
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(OpenApiProperties.class)
public class CommonSpringdocAutoConfiguration {

    /**
     * 构建默认 OpenAPI 文档Bean，容器不存在时才创建。
     * <p>配置来源于 {@link OpenApiProperties}；内置 http‑bearer JWT 安全模式。</p>
     * @param props openapi配置属性
     * @return OpenAPI 实例
     */
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI commonOpenAPI(OpenApiProperties props) {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title(props.title())
                        .description(props.description())
                        .version(props.version()))
                // 全局声明安全要求：Swagger‑UI界面默认展示bearerAuth认证框
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
