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
 * SpringDoc OpenAPI 自动配置（common-springdoc-spring-boot-starter）：
 * 统一提供 OpenAPI 文档模板（title/description 可配置，bearerAuth JWT 安全方案），
 * 各业务服务零配置获得 /v3/api-docs 与 /swagger-ui.html。
 * 服务可自行定义 {@link OpenAPI} Bean 覆盖默认模板（@ConditionalOnMissingBean）。
 * 仅加载了 springdoc 类（MVC/WebFlux starter）的应用生效。
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(OpenApiProperties.class)
public class CommonSpringdocAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI commonOpenAPI(OpenApiProperties props) {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title(props.title())
                        .description(props.description())
                        .version(props.version()))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
