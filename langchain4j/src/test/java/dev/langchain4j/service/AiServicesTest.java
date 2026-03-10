package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

class AiServicesTest {

    interface Assistant {

        String chat(String message);

        default int getInt() {
            return 42;
        }

        default int getException() {
            throw new RuntimeException("oops");
        }
    }

    private final ChatModel chatModel = mock(ChatModel.class);

    private final Assistant assistant =
            AiServices.builder(Assistant.class).chatModel(chatModel).build();

    @Test
    void should_not_call_chatModel_when_Object_methods_are_called() throws Exception {

        // when
        assistant.getClass();
        assistant.hashCode();
        assistant.equals(assistant);
        assistant.toString();
        synchronized (assistant) {
            assistant.notify();
            assistant.notifyAll();
        }

        // then
        verifyNoInteractions(chatModel);
    }

    @Test
    void test_default_method() {
        assertThat(assistant.getInt()).isEqualTo(42);
        verifyNoInteractions(chatModel);
    }

    @Test
    void test_default_method_throwing_exception() {
        assertThatThrownBy(() -> assistant.getException())
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("oops");
        verifyNoInteractions(chatModel);
    }

    @Test
    void test_getClass() {
        // dynamic proxies don't intercept final methods
        assertThat(assistant.getClass()).isNotEqualTo(Assistant.class);
    }

    @Test
    void test_hashCode() {
        assertThat(assistant.hashCode())
                .isNotZero()
                .isEqualTo(assistant.hashCode())
                .isEqualTo(System.identityHashCode(assistant))
                .isEqualTo(System.identityHashCode(assistant))
                .isNotEqualTo(AiServices.create(Assistant.class, chatModel).hashCode());
    }

    @Test
    void test_equals() throws Exception {
        assertThat(assistant.equals(assistant)).isTrue();
        assertThat(assistant.equals(new Object())).isFalse();
        assertThat(assistant.equals(AiServices.create(Assistant.class, chatModel)))
                .isFalse();
    }

    @Test
    void test_toString() throws Exception {
        assertThat(assistant.toString()).startsWith("dev.langchain4j.service.AiServicesTest$Assistant@");
    }

    @Test
    void should_handle_null_chat_model() {
        assertThatThrownBy(() ->
                        AiServices.builder(Assistant.class).chatModel(null).build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("chatModel");
    }

    @Test
    void should_handle_non_interface_class() {
        assertThatThrownBy(() ->
                        AiServices.builder(String.class).chatModel(chatModel).build())
                .isInstanceOf(IllegalConfigurationException.class);
    }

    @Test
    void should_create_different_instances_for_same_interface() {
        Assistant assistant1 =
                AiServices.builder(Assistant.class).chatModel(chatModel).build();

        Assistant assistant2 =
                AiServices.builder(Assistant.class).chatModel(chatModel).build();

        assertThat(assistant1).isNotSameAs(assistant2);
        assertThat(assistant1.equals(assistant2)).isFalse();
        assertThat(assistant1.hashCode()).isNotEqualTo(assistant2.hashCode());
    }

    @Test
    void should_preserve_interface_type() {
        assertThat(assistant).isInstanceOf(Assistant.class);
        assertThat(Assistant.class.isAssignableFrom(assistant.getClass())).isTrue();
    }

    interface ChefAssistant {

        @SystemMessage("You are a professional chef.")
        String answer(String question);
    }

    @Test
    void should_transform_system_message() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("Grill for 30 minutes."));

        ChefAssistant chef = AiServices.builder(ChefAssistant.class)
                .chatModel(chatModel)
                .systemMessageTransformer(msg -> msg + " Keep your answer to one sentence.")
                .build();

        // when
        chef.answer("How long should I grill chicken?");

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage("You are a professional chef. Keep your answer to one sentence."),
                        userMessage("How long should I grill chicken?"))
                .build());
    }

    @Test
    void should_transform_system_message_with_invocation_context() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("Grill for 30 minutes."));

        ChefAssistant chef = AiServices.builder(ChefAssistant.class)
                .chatModel(chatModel)
                .systemMessageTransformer((msg, ctx) -> msg + " Method: " + ctx.methodName() + ".")
                .build();

        // when
        chef.answer("How long should I grill chicken?");

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage("You are a professional chef. Method: answer."),
                        userMessage("How long should I grill chicken?"))
                .build());
    }

    @Test
    void should_add_system_message_via_transformer_when_none_is_configured() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("4"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .systemMessageTransformer(msg -> "You are a helpful assistant.")
                .build();

        // when
        assistant.chat("What is 2 + 2?");

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage("You are a helpful assistant."),
                        userMessage("What is 2 + 2?"))
                .build());
    }

    @Test
    void should_transform_system_message_from_system_message_provider() {

        // given
        ChatModel chatModel = spy(ChatModelMock.thatAlwaysResponds("4"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> "You are a helpful assistant.")
                .systemMessageTransformer(msg -> msg + " Always be concise.")
                .build();

        // when
        assistant.chat("What is 2 + 2?");

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage("You are a helpful assistant. Always be concise."),
                        userMessage("What is 2 + 2?"))
                .build());
    }
}
