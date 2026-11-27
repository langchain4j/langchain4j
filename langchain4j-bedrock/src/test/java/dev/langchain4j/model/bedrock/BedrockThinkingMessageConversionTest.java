/*
 * Copyright 2026 steadybit GmbH. All rights reserved.
 */

package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.THINKING_SIGNATURE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Tests how reasoning blocks of an assistant turn are read from a Converse response and sent back in follow-up
 * requests. Uses a test helper class to access protected methods.
 */
class BedrockThinkingMessageConversionTest {

    private static final String SIGNATURE = "EoAECpABCBEYAipARsBWFsXRge7q";

    /**
     * Test helper that exposes protected methods for testing.
     */
    private static class TestableModel extends AbstractBedrockChatModel {

        TestableModel(boolean returnThinking, boolean sendThinking) {
            super(new TestBuilder().returnThinking(returnThinking).sendThinking(sendThinking));
        }

        private static class TestBuilder extends AbstractBuilder<TestBuilder> {
            @Override
            public TestBuilder self() {
                return this;
            }
        }
    }

    @Test
    void should_send_reasoning_block_that_has_only_a_signature() {
        // given an assistant turn whose reasoning text is empty and whose reasoning is carried entirely by the
        // (encrypted) signature: this is what Claude models with adaptive thinking return by default
        TestableModel model = new TestableModel(true, true);
        ConverseResponse response = converseResponse(
                reasoningBlock("", SIGNATURE),
                ContentBlock.fromToolUse(ToolUseBlock.builder()
                        .toolUseId("tool-1")
                        .name("getWeather")
                        .input(Document.mapBuilder().build())
                        .build()));

        AiMessage aiMessage = model.aiMessageFrom(response);
        // "aiMessageFrom" turns the empty reasoning text into null, so only the signature survives
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attribute(THINKING_SIGNATURE_KEY, String.class)).isEqualTo(SIGNATURE);

        // when
        Message message = model.createAiMessage(aiMessage);

        // then the block must still be echoed back: Claude requires the assistant turn to be sent back unchanged
        ContentBlock content = message.content().get(0);
        assertThat(content.type()).isEqualTo(ContentBlock.Type.REASONING_CONTENT);
        ReasoningTextBlock reasoningText = content.reasoningContent().reasoningText();
        // "" and not null: "text" is required by the API, a reasoningText without it is rejected
        assertThat(reasoningText.text()).isEmpty();
        assertThat(reasoningText.signature()).isEqualTo(SIGNATURE);
        assertThat(message.content().get(1).type()).isEqualTo(ContentBlock.Type.TOOL_USE);
    }

    @Test
    void should_send_reasoning_block_that_has_only_a_signature_when_thinking_text_is_empty() {
        // given an AiMessage that carries an empty instead of a null thinking text,
        // for example one restored from a serialized chat memory
        AiMessage aiMessage = AiMessage.builder()
                .thinking("")
                .attributes(Map.of(THINKING_SIGNATURE_KEY, SIGNATURE))
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("tool-1")
                        .name("getWeather")
                        .arguments("{}")
                        .build()))
                .build();

        Message message = new TestableModel(true, true).createAiMessage(aiMessage);

        ReasoningTextBlock reasoningText =
                message.content().get(0).reasoningContent().reasoningText();
        assertThat(reasoningText.text()).isEmpty();
        assertThat(reasoningText.signature()).isEqualTo(SIGNATURE);
    }

    @Test
    void should_send_reasoning_block_with_both_text_and_signature() {
        AiMessage aiMessage = AiMessage.builder()
                .text("Hello")
                .thinking("Let me think about this")
                .attributes(Map.of(THINKING_SIGNATURE_KEY, SIGNATURE))
                .build();

        Message message = new TestableModel(true, true).createAiMessage(aiMessage);

        assertThat(message.content()).hasSize(2);
        ReasoningTextBlock reasoningText =
                message.content().get(0).reasoningContent().reasoningText();
        assertThat(reasoningText.text()).isEqualTo("Let me think about this");
        assertThat(reasoningText.signature()).isEqualTo(SIGNATURE);
        assertThat(message.content().get(1).text()).isEqualTo("Hello");
    }

    @Test
    void should_not_send_reasoning_block_when_there_is_neither_text_nor_signature() {
        AiMessage aiMessage = AiMessage.builder().text("Hello").build();

        Message message = new TestableModel(true, true).createAiMessage(aiMessage);

        assertThat(message.content()).noneMatch(block -> block.type() == ContentBlock.Type.REASONING_CONTENT);
    }

    @Test
    void should_not_send_reasoning_block_when_sendThinking_is_false() {
        AiMessage aiMessage = AiMessage.builder()
                .text("Hello")
                .thinking("Let me think about this")
                .attributes(Map.of(THINKING_SIGNATURE_KEY, SIGNATURE))
                .build();

        Message message = new TestableModel(true, false).createAiMessage(aiMessage);

        assertThat(message.content()).noneMatch(block -> block.type() == ContentBlock.Type.REASONING_CONTENT);
    }

    @Test
    void should_not_send_signature_only_reasoning_block_when_sendThinking_is_false() {
        AiMessage aiMessage = AiMessage.builder()
                .text("Hello")
                .attributes(Map.of(THINKING_SIGNATURE_KEY, SIGNATURE))
                .build();

        Message message = new TestableModel(true, false).createAiMessage(aiMessage);

        assertThat(message.content()).noneMatch(block -> block.type() == ContentBlock.Type.REASONING_CONTENT);
    }

    private static ContentBlock reasoningBlock(String text, String signature) {
        return ContentBlock.fromReasoningContent(ReasoningContentBlock.builder()
                .reasoningText(ReasoningTextBlock.builder()
                        .text(text)
                        .signature(signature)
                        .build())
                .build());
    }

    private static ConverseResponse converseResponse(ContentBlock... contents) {
        return ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(contents)
                                .build())
                        .build())
                .build();
    }
}
