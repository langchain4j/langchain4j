package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@ExtendWith(MockitoExtension.class)
class BedrockTokenCountEstimatorTest {

    private static final String TEST_MODEL_ID = "test-model-id";

    private static final ToolExecutionRequest TOOL_REQUEST_1 = ToolExecutionRequest.builder()
            .id("t1")
            .name("myTool")
            .arguments("{}")
            .build();

    private static final ToolExecutionRequest TOOL_REQUEST_2 = ToolExecutionRequest.builder()
            .id("t2")
            .name("myOtherTool")
            .arguments("{}")
            .build();

    private static final ToolExecutionRequest TOOL_REQUEST = TOOL_REQUEST_1;

    @Mock
    private BedrockRuntimeClient mockClient;

    private TokenCountEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = BedrockTokenCountEstimator.builder()
                .client(mockClient)
                .modelId(TEST_MODEL_ID)
                .build();
    }

    @Test
    void should_estimate_token_count_in_text() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(5).build());

        int count = estimator.estimateTokenCountInText("Hello, world!");

        assertThat(count).isEqualTo(5);
        verify(mockClient).countTokens(any(CountTokensRequest.class));
    }

    @Test
    void should_include_system_message_in_token_count() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(15).build());

        int count = estimator.estimateTokenCountInMessages(
                List.of(SystemMessage.from("You are a helpful assistant."), UserMessage.from("Hello")));

        assertThat(count).isEqualTo(15);

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());

        CountTokensRequest request = captor.getValue();
        assertThat(request.modelId()).isEqualTo(TEST_MODEL_ID);
        assertThat(request.input().converse().system()).isNotEmpty();
    }

    @Test
    void should_include_bedrock_system_message_in_token_count() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(15).build());

        BedrockSystemMessage bedrockSystem = BedrockSystemMessage.builder()
                .addText("You are a helpful assistant.")
                .addText("Be concise.")
                .build();

        int count = estimator.estimateTokenCountInMessages(List.of(bedrockSystem, UserMessage.from("Hello")));

        assertThat(count).isEqualTo(15);

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());

        CountTokensRequest request = captor.getValue();
        assertThat(request.input().converse().system()).hasSize(2);
    }

    @Test
    void should_handle_multiple_system_messages() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(12).build());

        int count = estimator.estimateTokenCountInMessages(List.of(
                SystemMessage.from("You are helpful."),
                SystemMessage.from("You are concise."),
                UserMessage.from("Hi")));

        assertThat(count).isEqualTo(12);

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        assertThat(captor.getValue().input().converse().system()).hasSize(2);
    }

    @Test
    void should_handle_conversation_with_multiple_turns() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(25).build());

        int count = estimator.estimateTokenCountInMessages(List.of(
                UserMessage.from("What is 2+2?"),
                AiMessage.from("2+2 equals 4."),
                UserMessage.from("What about 3+3?"),
                AiMessage.from("3+3 equals 6.")));

        assertThat(count).isEqualTo(25);

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        assertThat(captor.getValue().input().converse().messages()).hasSize(4);
    }

    @Test
    void should_fail_when_model_id_is_null() {
        assertThatThrownBy(() -> BedrockTokenCountEstimator.builder()
                        .client(mockClient)
                        .modelId(null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_fail_when_model_id_is_blank() {
        assertThatThrownBy(() -> BedrockTokenCountEstimator.builder()
                        .client(mockClient)
                        .modelId("  ")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Inline sanitization tests (orphaned tool_use / tool_result handling) ---

    @Test
    void should_inject_dummy_result_for_trailing_orphaned_tool_use() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        estimator.estimateTokenCountInMessages(List.of(UserMessage.from("hi"), AiMessage.from(TOOL_REQUEST)));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        // USER, ASSISTANT(tool_use), USER(dummy tool_result)
        assertThat(sent).hasSize(3);
        assertThat(sent.get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
        assertThat(sent.get(1).content().stream().anyMatch(b -> b.toolUse() != null))
                .isTrue();
        assertThat(sent.get(2).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(2).content().stream().anyMatch(b -> b.toolResult() != null))
                .isTrue();
        assertThat(sent.get(2).content().get(0).toolResult().toolUseId()).isEqualTo("t1");
    }

    @Test
    void should_inject_dummy_result_for_trailing_orphaned_tool_use_with_text() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        AiMessage aiWithTextAndTool = new AiMessage("Let me check", List.of(TOOL_REQUEST));

        estimator.estimateTokenCountInMessages(List.of(UserMessage.from("hi"), aiWithTextAndTool));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        // USER, ASSISTANT(text + tool_use preserved), USER(dummy tool_result)
        assertThat(sent).hasSize(3);
        assertThat(sent.get(1).content()).hasSize(2);
        assertThat(sent.get(1).content().get(0).text()).isEqualTo("Let me check");
        assertThat(sent.get(1).content().get(1).toolUse()).isNotNull();
        assertThat(sent.get(2).content().get(0).toolResult().toolUseId()).isEqualTo("t1");
    }

    @Test
    void should_skip_leading_orphaned_tool_result() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        estimator.estimateTokenCountInMessages(
                List.of(ToolExecutionResultMessage.from("t1", "myTool", "result"), AiMessage.from("The result is 42")));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).role()).isEqualTo(ConversationRole.ASSISTANT);
    }

    @Test
    void should_inject_dummy_result_for_mid_conversation_orphaned_tool_use() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        // AiMessage with tool_use followed by UserMessage (no tool_result in between)
        estimator.estimateTokenCountInMessages(List.of(
                UserMessage.from("hi"),
                AiMessage.from(TOOL_REQUEST),
                UserMessage.from("never mind"),
                AiMessage.from("OK")));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        // USER, ASSISTANT(tool_use), USER(dummy tool_result), USER, ASSISTANT
        assertThat(sent).hasSize(5);
        assertThat(sent.get(0).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
        assertThat(sent.get(1).content().stream().anyMatch(b -> b.toolUse() != null))
                .isTrue();
        assertThat(sent.get(2).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(2).content().get(0).toolResult().toolUseId()).isEqualTo("t1");
        assertThat(sent.get(3).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(4).content().get(0).text()).isEqualTo("OK");
    }

    @Test
    void should_inject_dummy_result_for_mid_conversation_orphaned_tool_use_with_text() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        AiMessage aiWithTextAndTool = new AiMessage("Let me check", List.of(TOOL_REQUEST));

        // AiMessage with text+tool_use followed by UserMessage (no tool_result)
        estimator.estimateTokenCountInMessages(List.of(
                UserMessage.from("hi"), aiWithTextAndTool, UserMessage.from("never mind"), AiMessage.from("OK")));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        // USER, ASSISTANT(text + tool_use), USER(dummy tool_result), USER, ASSISTANT
        assertThat(sent).hasSize(5);
        assertThat(sent.get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
        assertThat(sent.get(1).content()).hasSize(2);
        assertThat(sent.get(1).content().get(0).text()).isEqualTo("Let me check");
        assertThat(sent.get(1).content().get(1).toolUse()).isNotNull();
        assertThat(sent.get(2).content().get(0).toolResult().toolUseId()).isEqualTo("t1");
    }

    @Test
    void should_preserve_valid_tool_use_with_tool_result() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        estimator.estimateTokenCountInMessages(List.of(
                UserMessage.from("hi"),
                AiMessage.from(TOOL_REQUEST),
                ToolExecutionResultMessage.from("t1", "myTool", "result"),
                AiMessage.from("Done")));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        assertThat(sent).hasSize(4);
        assertThat(sent.get(0).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
        assertThat(sent.get(1).content().stream().anyMatch(b -> b.toolUse() != null))
                .isTrue();
        assertThat(sent.get(2).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(2).content().stream().anyMatch(b -> b.toolResult() != null))
                .isTrue();
        assertThat(sent.get(3).role()).isEqualTo(ConversationRole.ASSISTANT);
    }

    @Test
    void should_include_thinking_in_token_count() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(20).build());

        AiMessage aiWithThinking = AiMessage.builder()
                .thinking("Let me reason about this")
                .text("The answer is 42")
                .build();

        estimator.estimateTokenCountInMessages(List.of(UserMessage.from("hi"), aiWithThinking));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).content()).hasSize(2);
        assertThat(sent.get(1).content().get(0).reasoningContent()).isNotNull();
        assertThat(sent.get(1).content().get(1).text()).isEqualTo("The answer is 42");
    }

    @Test
    void should_inject_dummy_result_for_unmatched_tool_use_on_partial_results() {
        when(mockClient.countTokens(any(CountTokensRequest.class)))
                .thenReturn(CountTokensResponse.builder().inputTokens(3).build());

        // AiMessage requests 2 tools, only 1 result arrives (partial)
        estimator.estimateTokenCountInMessages(List.of(
                UserMessage.from("hi"),
                AiMessage.from(List.of(TOOL_REQUEST_1, TOOL_REQUEST_2)),
                ToolExecutionResultMessage.from("t1", "myTool", "result1"),
                AiMessage.from("Done")));

        ArgumentCaptor<CountTokensRequest> captor = ArgumentCaptor.forClass(CountTokensRequest.class);
        verify(mockClient).countTokens(captor.capture());
        List<Message> sent = captor.getValue().input().converse().messages();
        // USER, ASSISTANT(both tool_use kept), USER(real t1 result + dummy t2 result), ASSISTANT
        assertThat(sent).hasSize(4);
        assertThat(sent.get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
        assertThat(sent.get(1).content().stream()
                        .filter(b -> b.toolUse() != null)
                        .count())
                .isEqualTo(2);
        assertThat(sent.get(2).role()).isEqualTo(ConversationRole.USER);
        assertThat(sent.get(2).content().stream()
                        .filter(b -> b.toolResult() != null)
                        .count())
                .isEqualTo(2);
    }
}
