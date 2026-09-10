package com.agentdoc.agent.a2a.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A2aJacksonConfigTest {

    @Test
    void shouldDeserializeTextAndDataPartsFromMessageSendParams() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new A2aJacksonConfig().a2aPartJacksonCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        MessageSendParams params = MessageSendParams.builder()
                .message(Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .messageId("message-1")
                        .parts(new TextPart("优化文档"), new DataPart(Map.of("taskId", 1L)))
                        .build())
                .build();

        MessageSendParams decoded = mapper.readValue(mapper.writeValueAsString(params), MessageSendParams.class);

        assertThat(decoded.message().parts()).hasSize(2);
        assertThat(decoded.message().parts().get(0)).isInstanceOf(TextPart.class);
        assertThat(decoded.message().parts().get(1)).isInstanceOf(DataPart.class);
    }
}
