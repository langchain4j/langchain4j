package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for lenient mode in prompt template resolution.
 * Covers: @SystemMessage(lenient), @UserMessage(lenient),
 * systemMessageLenient(), and userMessageLenient() builder methods.
 */
@ExtendWith(MockitoExtension.class)
public class AiServicesLenientTest {

    @Spy
    ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(model);
    }

    // ==================== System Message Lenient ====================

    interface SystemMessageLenientFromAnnotation {

        @SystemMessage(value = "You are a {{role}} assistant. {{extra}}", lenient = true)
        String chat(@V("role") String role, @UserMessage String userMessage);
    }

    @Test
    void system_message_annotation_lenient_true_should_keep_unresolved_variables() {

        // given
        SystemMessageLenientFromAnnotation aiService = AiServices.builder(SystemMessageLenientFromAnnotation.class)
                .chatModel(model)
                .build();

        // when-then
        assertThat(aiService.chat("helpful", "Hello")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(systemMessage("You are a helpful assistant. {{extra}}"), userMessage("Hello"))
                        .build());
    }

    interface SystemMessageStrictFromAnnotation {

        @SystemMessage(value = "You are a {{role}} assistant. {{extra}}", lenient = false)
        String chat(@V("role") String role, @UserMessage String userMessage);
    }

    @Test
    void system_message_annotation_lenient_false_should_throw_on_unresolved_variables() {

        // given
        SystemMessageStrictFromAnnotation aiService = AiServices.builder(SystemMessageStrictFromAnnotation.class)
                .chatModel(model)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat("helpful", "Hello"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extra");
    }

    interface SystemMessageFromProvider {

        String chat(@V("role") String role, @UserMessage String userMessage);
    }

    @Test
    void system_message_provider_lenient_true_should_keep_unresolved_variables() {

        // given
        SystemMessageFromProvider aiService = AiServices.builder(SystemMessageFromProvider.class)
                .chatModel(model)
                .systemMessageProvider(memoryId -> "You are a {{role}} assistant. {{extra}}")
                .systemMessageLenient(true)
                .build();

        // when-then
        assertThat(aiService.chat("helpful", "Hello")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(systemMessage("You are a helpful assistant. {{extra}}"), userMessage("Hello"))
                        .build());
    }

    @Test
    void system_message_provider_lenient_false_default_should_throw_on_unresolved_variables() {

        // given
        SystemMessageFromProvider aiService = AiServices.builder(SystemMessageFromProvider.class)
                .chatModel(model)
                .systemMessageProvider(memoryId -> "You are a {{role}} assistant. {{extra}}")
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat("helpful", "Hello"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extra");
    }

    // ==================== User Message Lenient ====================

    interface UserMessageLenientFromMethodAnnotation {

        @UserMessage(value = "You are a {{role}} assistant. {{extra}}", lenient = true)
        String chat(@V("role") String role);
    }

    @Test
    void user_message_method_annotation_lenient_true_should_keep_unresolved_variables() {

        // given
        UserMessageLenientFromMethodAnnotation aiService = AiServices.builder(
                        UserMessageLenientFromMethodAnnotation.class)
                .chatModel(model)
                .build();

        // when-then
        assertThat(aiService.chat("helpful")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(userMessage("You are a helpful assistant. {{extra}}"))
                        .build());
    }

    interface UserMessageStrictFromMethodAnnotation {

        @UserMessage(value = "You are a {{role}} assistant. {{extra}}", lenient = false)
        String chat(@V("role") String role);
    }

    @Test
    void user_message_method_annotation_lenient_false_should_throw_on_unresolved_variables() {

        // given
        UserMessageStrictFromMethodAnnotation aiService = AiServices.builder(
                        UserMessageStrictFromMethodAnnotation.class)
                .chatModel(model)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat("helpful"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extra");
    }

    interface UserMessageLenientFromParameterAnnotation {

        String chat(@UserMessage(lenient = true) String template, @V("role") String role);
    }

    @Test
    void user_message_parameter_annotation_lenient_true_should_keep_unresolved_variables() {

        // given
        UserMessageLenientFromParameterAnnotation aiService = AiServices.builder(
                        UserMessageLenientFromParameterAnnotation.class)
                .chatModel(model)
                .build();

        // when-then
        assertThat(aiService.chat("You are a {{role}} assistant. {{extra}}", "helpful"))
                .containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(userMessage("You are a helpful assistant. {{extra}}"))
                        .build());
    }

    interface UserMessageFromProvider {

        String chat(@V("role") String role);
    }

    @Test
    void user_message_provider_lenient_true_should_keep_unresolved_variables() {

        // given
        UserMessageFromProvider aiService = AiServices.builder(UserMessageFromProvider.class)
                .chatModel(model)
                .userMessageProvider(memoryId -> "You are a {{role}} assistant. {{extra}}")
                .userMessageLenient(true)
                .build();

        // when-then
        assertThat(aiService.chat("helpful")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(userMessage("You are a helpful assistant. {{extra}}"))
                        .build());
    }

    @Test
    void user_message_provider_lenient_false_default_should_throw_on_unresolved_variables() {

        // given
        UserMessageFromProvider aiService = AiServices.builder(UserMessageFromProvider.class)
                .chatModel(model)
                .userMessageProvider(memoryId -> "You are a {{role}} assistant. {{extra}}")
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat("helpful"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extra");
    }

    interface UserMessageFromOnlyArgument {

        String chat(String userMessage);
    }

    @Test
    void user_message_only_argument_lenient_true_should_keep_placeholders() {

        // given
        UserMessageFromOnlyArgument aiService = AiServices.builder(UserMessageFromOnlyArgument.class)
                .chatModel(model)
                .userMessageLenient(true)
                .build();

        // when-then
        assertThat(aiService.chat("You are a {{role}} assistant. {{extra}}")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(userMessage("You are a {{role}} assistant. {{extra}}"))
                        .build());
    }

    @Test
    void user_message_only_argument_lenient_false_default_should_throw_on_unresolved_variables() {

        // given
        UserMessageFromOnlyArgument aiService = AiServices.builder(UserMessageFromOnlyArgument.class)
                .chatModel(model)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat("You are a {{role}} assistant. {{extra}}"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");
    }
}
