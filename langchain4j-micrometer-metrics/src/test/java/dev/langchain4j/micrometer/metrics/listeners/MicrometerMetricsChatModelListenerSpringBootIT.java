package dev.langchain4j.micrometer.metrics.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot integration test for {@link MicrometerMetricsChatModelListener}.
 * Verifies that the listener can be configured as a Spring bean and correctly records
 * metrics using a Spring-managed {@link MeterRegistry}.
 * <p>
 * This test uses Spring Boot 3.5.x, which is the version currently supported by LangChain4j.
 * Spring Boot 4.x is not yet compatible due to dependency conflicts (e.g. the parent POM pins
 * logback-classic to a version that is incompatible with the logback-core version brought in by
 * Spring Boot 4.x).
 */
@SpringBootTest(classes = MicrometerMetricsChatModelListenerSpringBootIT.TestApplication.class)
class MicrometerMetricsChatModelListenerSpringBootIT {

    @SpringBootApplication
    static class TestApplication {

        @Bean
        MicrometerMetricsChatModelListener micrometerMetricsChatModelListener(MeterRegistry meterRegistry) {
            return new MicrometerMetricsChatModelListener(meterRegistry);
        }
    }

    @Autowired
    MicrometerMetricsChatModelListener listener;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void should_autowire_listener_with_spring_managed_meter_registry() {
        assertThat(listener).isNotNull();
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    void should_record_metrics_using_spring_managed_meter_registry() {
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(new AiMessage("Hello"))
                .tokenUsage(new TokenUsage(15, 25))
                .modelName("gpt-4o")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .modelName("gpt-4o")
                .build();

        ChatModelResponseContext responseContext =
                new ChatModelResponseContext(chatResponse, chatRequest, ModelProvider.MICROSOFT_FOUNDRY, new HashMap<>());

        listener.onResponse(responseContext);

        // Verify input token metric was recorded
        assertThat(meterRegistry
                        .get(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "azure.ai.inference")
                        .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "gpt-4o")
                        .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "gpt-4o")
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .summary()
                        .totalAmount())
                .isEqualTo(15.0);

        // Verify output token metric was recorded
        assertThat(meterRegistry
                        .get(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "azure.ai.inference")
                        .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "gpt-4o")
                        .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "gpt-4o")
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .summary()
                        .totalAmount())
                .isEqualTo(25.0);
    }
}
