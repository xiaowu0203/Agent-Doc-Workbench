package com.agentdoc.agent.a2a.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A 协议对象的 Jackson 多态反序列化配置。
 */
@Configuration
public class A2aJacksonConfig {

    /**
     * 根据 Part 的结构字段推断文本或数据片段类型。
     *
     * @return A2A Part 类型适配器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer a2aPartJacksonCustomizer() {
        return builder -> builder.mixIn(Part.class, PartMixin.class);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(TextPart.class),
            @JsonSubTypes.Type(DataPart.class)
    })
    private interface PartMixin {
    }
}
