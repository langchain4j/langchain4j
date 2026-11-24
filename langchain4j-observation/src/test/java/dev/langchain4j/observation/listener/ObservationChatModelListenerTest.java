package dev.langchain4j.observation.listener;

import static dev.langchain4j.observation.convention.ChatModelDocumentation.LowCardinalityValues.PROVIDER_NAME;
import static dev.langchain4j.observation.listener.ObservationChatModelListener.TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservationChatModelListenerTest {

    private TestObservationRegistry observationRegistry;
    private MeterRegistry meterRegistry;
    private ObservationChatModelListener listener;

    @BeforeEach
    void setUp() {
        observationRegistry = TestObservationRegistry.create();
        meterRegistry = new SimpleMeterRegistry();
        listener = new ObservationChatModelListener(observationRegistry, meterRegistry);
    }

    @Test
    void should_report_the_otel_provider_name_on_the_observation_and_on_token_usage() {
        chat(ModelProvider.OPEN_AI);

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(PROVIDER_NAME.asString(), "openai"));
        assertThat(meterRegistry
                        .find(TOKEN_USAGE)
                        .tag(PROVIDER_NAME.asString(), "openai")
                        .summaries())
                .hasSize(2);
    }

    @Test
    void should_report_unknown_when_the_model_provider_is_missing() {
        chat(null);

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(PROVIDER_NAME.asString(), "unknown"));
        assertThat(meterRegistry
                        .find(TOKEN_USAGE)
                        .tag(PROVIDER_NAME.asString(), "unknown")
                        .summaries())
                .hasSize(2);
    }

    private void chat(ModelProvider modelProvider) {
        // one call passes the same attributes map to every callback, as ChatModel does
        Map<Object, Object> attributes = new HashMap<>();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("gpt-4o-mini")
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .modelName("gpt-4o-mini")
                .tokenUsage(new TokenUsage(10, 20))
                .build();

        listener.onRequest(ChatModelRequestContext.builder()
                .chatRequest(chatRequest)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .build());
        listener.onResponse(ChatModelResponseContext.builder()
                .chatResponse(chatResponse)
                .chatRequest(chatRequest)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .build());
    }
}
