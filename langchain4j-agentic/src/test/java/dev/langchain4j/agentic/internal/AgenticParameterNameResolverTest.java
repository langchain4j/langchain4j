package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgenticParameterNameResolverTest {

    @Test
    void should_prioritize_v_annotation_over_java_parameter_name() throws NoSuchMethodException {
        Method method = TestAiService.class.getMethod("chat", String.class, String.class);
        Parameter parameter = method.getParameters()[1];

        String variableName = new AgenticParameterNameResolver().getVariableName(parameter);

        assertThat(variableName).isEqualTo("xx");
    }

    @Test
    void should_render_system_message_template_using_v_annotation_name() {
        CapturingChatModel model = new CapturingChatModel();
        TestAiService aiService =
                AiServices.builder(TestAiService.class).chatModel(model).build();

        String response = aiService.chat("hello", "value-from-v");

        assertThat(response).isEqualTo("ok");
        assertThat(model.messages())
                .containsExactly(
                        SystemMessage.from("Use value-from-v."),
                        dev.langchain4j.data.message.UserMessage.from("hello"));
    }

    @Test
    void should_render_system_message_provider_template_using_v_annotation_name() {
        CapturingChatModel model = new CapturingChatModel();
        TestAiService aiService = AiServices.builder(TestAiService.class)
                .chatModel(model)
                .systemMessageProvider(memoryId -> "Use {{xx}}.")
                .build();

        String response = aiService.chatWithProvider("hello", "value-from-v");

        assertThat(response).isEqualTo("ok");
        assertThat(model.messages())
                .containsExactly(
                        SystemMessage.from("Use value-from-v."),
                        dev.langchain4j.data.message.UserMessage.from("hello"));
    }

    interface TestAiService {

        @dev.langchain4j.service.SystemMessage("Use {{xx}}.")
        String chat(@UserMessage String message, @V("xx") String yy);

        String chatWithProvider(@UserMessage String message, @V("xx") String yy);
    }

    static class CapturingChatModel implements ChatModel {

        private List<ChatMessage> messages;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            this.messages = chatRequest.messages();
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }

        List<ChatMessage> messages() {
            return messages;
        }
    }
}
