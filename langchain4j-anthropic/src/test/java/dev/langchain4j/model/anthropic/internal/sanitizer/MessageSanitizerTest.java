package dev.langchain4j.model.anthropic.internal.sanitizer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageSanitizerTest {

    // Default expected message values
    private static final String EXPECTED_USER_MESSAGE_CONTENT = "User message";
    private static final String EXPECTED_AI_MESSAGE_CONTENT = "AI message";
    private static final String EXPECTED_SYSTEM_MESSAGE_CONTENT = "System message";

    @Test
    void strip_system_message() {
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(new SystemMessage(EXPECTED_SYSTEM_MESSAGE_CONTENT));
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(1);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
    }

    @Test
    void strip_multiple_system_messages() {
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(new SystemMessage("System message 1"));
        messages.add(new SystemMessage("System message 2"));
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(1);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
    }

    @Test
    void remove_single_pair_of_consecutive_user_messages() {
        List<ChatMessage> messages = new ArrayList<>();
        String userMessage2 = "User message 2";
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));
        messages.add(new UserMessage(userMessage2));
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(EXPECTED_AI_MESSAGE_CONTENT);
    }

    @Test
    void remove_multiple_pairs_of_consecutive_user_messages() {
        List<ChatMessage> messages = new ArrayList<>();
        String userMessage1 = "User message 1";
        String userMessage2 = "User message 2";
        String aiMessage1 = "AI message 1";
        String userMessage3 = "User message 3";
        String userMessage4 = "User message 4";
        String aiMessage2 = "AI message 2";

        messages.add(new UserMessage(userMessage1));
        messages.add(new UserMessage(userMessage2));
        messages.add(new AiMessage(aiMessage1));
        messages.add(new UserMessage(userMessage3));
        messages.add(new UserMessage(userMessage4));
        messages.add(new AiMessage(aiMessage2));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(4);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(sanitized.get(2)).isInstanceOf(UserMessage.class);
        assertThat(sanitized.get(3)).isInstanceOf(AiMessage.class);

        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(userMessage1);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(aiMessage1);
        assertThat(((UserMessage) sanitized.get(2)).singleText()).isEqualTo(userMessage3);
        assertThat(((AiMessage) sanitized.get(3)).text()).isEqualTo(aiMessage2);
    }

    @Test
    void ai_message_after_user_message() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(EXPECTED_AI_MESSAGE_CONTENT);
    }

    @Test
    void first_message_is_user_message_no_change() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(EXPECTED_AI_MESSAGE_CONTENT);
    }

    @Test
    void first_message_is_system_message() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(EXPECTED_SYSTEM_MESSAGE_CONTENT));
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(EXPECTED_AI_MESSAGE_CONTENT);
    }

    @Test
    void first_message_is_ai_message() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));
        messages.add(new UserMessage(EXPECTED_USER_MESSAGE_CONTENT));
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(EXPECTED_USER_MESSAGE_CONTENT);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(EXPECTED_AI_MESSAGE_CONTENT);
    }

    @Test
    void invalid_starting_message_with_invalid_user_pair() {
        List<ChatMessage> messages = new ArrayList<>();
        String userMessage1 = "User message 1";
        String userMessage2 = "User message 2";
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));
        messages.add(new UserMessage(userMessage1));
        messages.add(new UserMessage(userMessage2));
        messages.add(new AiMessage(EXPECTED_AI_MESSAGE_CONTENT));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(userMessage1);
        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(1)).text()).isEqualTo(EXPECTED_AI_MESSAGE_CONTENT);
    }

    @Test
    void tool_execution_messages() {
        String expectedUserMessageContent = "What is the product of 2x2?";
        String expectedAiMessageAfterTool = "The answer for 2x2 is 4";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("The agent exists to help with arithmetic problems."));
        messages.add(UserMessage.from(expectedUserMessageContent));
        messages.add(AiMessage.from(ToolExecutionRequest.builder()
                .id("12345")
                .name("calculator")
                .arguments("{\"first\": 2, \"second\": 2}")
                .build()));
        messages.add(ToolExecutionResultMessage.from("12345", "calculator", "4"));
        messages.add(AiMessage.from(expectedAiMessageAfterTool));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertThat(sanitized).hasSize(4);

        assertThat(sanitized.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitized.get(0)).singleText()).isEqualTo(expectedUserMessageContent);

        assertThat(sanitized.get(1)).isInstanceOf(AiMessage.class);
        ToolExecutionRequest toolExecutionRequest =
                ((AiMessage) sanitized.get(1)).toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isEqualTo("12345");
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{\"first\": 2, \"second\": 2}");

        assertThat(sanitized.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) sanitized.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo("12345");
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("calculator");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("4");

        assertThat(sanitized.get(3)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitized.get(3)).text()).isEqualTo(expectedAiMessageAfterTool);
    }
}
