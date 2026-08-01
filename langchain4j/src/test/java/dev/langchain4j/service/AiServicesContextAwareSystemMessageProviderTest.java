package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

class AiServicesContextAwareSystemMessageProviderTest {

    interface Assistant {

        String chat(String message);

        String chatWithVariables(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage);
    }

    @Test
    void should_provide_system_message_with_invocation_context() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("4"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .systemMessageProviderWithContext(ctx -> {
                    assertThat(ctx.userMessage()).isNull();
                    assertThat(ctx.methodName()).isEqualTo("chat");
                    assertThat(ctx.methodArguments()).containsExactly("What is 2 + 2?");
                    return "Method: " + ctx.methodName()
                            + ". Input: " + ctx.methodArguments().get(0)
                            + ".";
                })
                .build();

        // when
        assistant.chat("What is 2 + 2?");

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(systemMessage("Method: chat. Input: What is 2 + 2?."), userMessage("What is 2 + 2?"))
                        .build());
    }

    @Test
    void should_transform_system_message_from_context_aware_system_message_provider() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("4"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .systemMessageProviderWithContext(ctx -> "Method: " + ctx.methodName() + ".")
                .systemMessageTransformer((msg, ctx) -> msg + " Be concise.")
                .build();

        // when
        assistant.chat("What is 2 + 2?");

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(systemMessage("Method: chat. Be concise."), userMessage("What is 2 + 2?"))
                        .build());
    }

    @Test
    void should_resolve_template_variables_from_context_aware_system_message_provider() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("Berlin"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .systemMessageProviderWithContext(ctx -> "Given a country, answer with {{answerInstructions}}")
                .build();

        // when
        String response = assistant.chatWithVariables("the name of its capital", "Country: Germany");

        // then
        assertThat(response).containsIgnoringCase("Berlin");
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("Given a country, answer with the name of its capital"),
                                userMessage("Country: Germany"))
                        .build());
    }
}
