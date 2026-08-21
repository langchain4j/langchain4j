package dev.langchain4j.observation.convention;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observation.context.ChatModelObservationContext;
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

    private static ChatModelObservationContext observationContext(ChatRequest chatRequest) {
        ChatModelRequestContext requestContext =
                new ChatModelRequestContext(chatRequest, ModelProvider.OPEN_AI, new HashMap<>());
        return new ChatModelObservationContext(requestContext, null, null);
    }
}
