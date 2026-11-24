package dev.langchain4j.observation.convention;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observation.context.ChatModelObservationContext;
import io.micrometer.common.KeyValue;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class DefaultChatModelConventionTest {

    private final DefaultChatModelConvention convention = new DefaultChatModelConvention();

    @Test
    void getContextualName_should_separate_operation_and_model_with_a_space() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("gpt-4o-mini")
                .build();
        ChatModelObservationContext context = observationContext(chatRequest);

        assertThat(convention.getContextualName(context)).isEqualTo("chat gpt-4o-mini");
    }

    @Test
    void getContextualName_should_fall_back_to_unknown_when_model_name_is_missing() {
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hello")).build();
        ChatModelObservationContext context = observationContext(chatRequest);

        assertThat(convention.getContextualName(context)).isEqualTo("chat unknown");
    }

    @Test
    void getLowCardinalityKeyValues_should_use_the_otel_provider_name() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("gpt-4o-mini")
                .build();
        ChatModelObservationContext context = observationContext(chatRequest);

        assertThat(convention.getLowCardinalityKeyValues(context))
                .contains(KeyValue.of("gen_ai.provider.name", "openai"));
    }

    @Test
    void getLowCardinalityKeyValues_should_fall_back_to_unknown_when_provider_is_missing() {
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hello")).build();
        ChatModelObservationContext context = observationContext(chatRequest, null);

        assertThat(convention.getLowCardinalityKeyValues(context))
                .contains(KeyValue.of("gen_ai.provider.name", "unknown"));
    }

    private static ChatModelObservationContext observationContext(ChatRequest chatRequest) {
        return observationContext(chatRequest, ModelProvider.OPEN_AI);
    }

    private static ChatModelObservationContext observationContext(
            ChatRequest chatRequest, ModelProvider modelProvider) {
        ChatModelRequestContext requestContext = ChatModelRequestContext.builder()
                .chatRequest(chatRequest)
                .modelProvider(modelProvider)
                .attributes(new HashMap<>())
                .build();
        return new ChatModelObservationContext(requestContext, null, null);
    }
}
