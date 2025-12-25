package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * Tests for extractSystemMessages() behavior with BedrockSystemMessage.
 * Uses a test helper class to access protected methods.
 */
class BedrockSystemMessageExtractionTest {

    /**
     * Test helper that exposes protected methods for testing.
     */
    private static class TestableExtractor extends AbstractBedrockChatModel {
        TestableExtractor() {
            super(new TestBuilder());
        }

        public List<SystemContentBlock> testExtractSystemMessages(
                List<ChatMessage> messages, BedrockCachePointPlacement placement) {
            return extractSystemMessages(messages, placement);
        }

        private static class TestBuilder extends AbstractBuilder<TestBuilder> {
            @Override
            public TestBuilder self() {
                return this;
            }
        }
    }

    private final TestableExtractor extractor = new TestableExtractor();

    // === Basic Extraction Tests ===

    @Test
    void should_extract_bedrock_system_message_without_cache_points() {
        BedrockSystemMessage bedrockMsg = BedrockSystemMessage.builder()
                .addText("First block")
                .addText("Second block")
                .build();

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(
                List.of(bedrockMsg), null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("First block");
        assertThat(result.get(1).text()).isEqualTo("Second block");
    }

    @Test
    void should_extract_bedrock_system_message_with_cache_points() {
        BedrockSystemMessage bedrockMsg = BedrockSystemMessage.builder()
                .addText("First block")
                .addTextWithCachePoint("Cached block")
                .addText("Third block")
                .build();

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(
                List.of(bedrockMsg), null);

        // Should have 4 blocks: text, text, cache_point, text
        assertThat(result).hasSize(4);
        assertThat(result.get(0).text()).isEqualTo("First block");
        assertThat(result.get(1).text()).isEqualTo("Cached block");
        assertThat(result.get(2).cachePoint()).isNotNull();
        assertThat(result.get(3).text()).isEqualTo("Third block");
    }

    @Test
    void should_extract_core_system_message() {
        SystemMessage coreMsg = SystemMessage.from("Core system message");

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(
                List.of(coreMsg), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("Core system message");
    }

    // === Mixed Message Types Tests ===

    @Test
    void should_handle_mixed_system_and_bedrock_system_messages() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("Core message"),
                BedrockSystemMessage.builder()
                        .addTextWithCachePoint("Bedrock cached")
                        .build());

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(messages, null);

        // Core message + Bedrock text + cache point
        assertThat(result).hasSize(3);
        assertThat(result.get(0).text()).isEqualTo("Core message");
        assertThat(result.get(1).text()).isEqualTo("Bedrock cached");
        assertThat(result.get(2).cachePoint()).isNotNull();
    }

    @Test
    void should_apply_after_system_only_when_last_is_core_system_message() {
        // When last is BedrockSystemMessage, AFTER_SYSTEM should NOT be applied
        List<ChatMessage> messagesEndingWithBedrock = Arrays.asList(
                SystemMessage.from("Core message"),
                BedrockSystemMessage.from("Bedrock message"));

        List<SystemContentBlock> result1 = extractor.testExtractSystemMessages(
                messagesEndingWithBedrock, BedrockCachePointPlacement.AFTER_SYSTEM);

        // No extra cache point because last is BedrockSystemMessage
        assertThat(result1).hasSize(2);
        assertThat(result1.stream().filter(b -> b.cachePoint() != null).count()).isZero();

        // When last is core SystemMessage, AFTER_SYSTEM SHOULD be applied
        List<ChatMessage> messagesEndingWithCore = Arrays.asList(
                BedrockSystemMessage.from("Bedrock message"),
                SystemMessage.from("Core message"));

        List<SystemContentBlock> result2 = extractor.testExtractSystemMessages(
                messagesEndingWithCore, BedrockCachePointPlacement.AFTER_SYSTEM);

        // Extra cache point because last is core SystemMessage
        assertThat(result2).hasSize(3);
        assertThat(result2.get(2).cachePoint()).isNotNull();
    }

    @Test
    void should_not_apply_after_system_when_only_bedrock_system_message() {
        List<ChatMessage> messages = List.of(
                BedrockSystemMessage.builder()
                        .addText("Bedrock only")
                        .build());

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(
                messages, BedrockCachePointPlacement.AFTER_SYSTEM);

        // No AFTER_SYSTEM cache point added
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("Bedrock only");
    }

    // === Null Handling Tests ===

    @Test
    void should_handle_null_messages_list() {
        List<SystemContentBlock> result = extractor.testExtractSystemMessages(null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void should_skip_null_elements_in_messages_list() {
        List<ChatMessage> messagesWithNull = new ArrayList<>();
        messagesWithNull.add(SystemMessage.from("First"));
        messagesWithNull.add(null);
        messagesWithNull.add(SystemMessage.from("Third"));

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(messagesWithNull, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("First");
        assertThat(result.get(1).text()).isEqualTo("Third");
    }

    // === Filter Tests ===

    @Test
    void should_ignore_non_system_messages() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("System"),
                UserMessage.from("User"),
                BedrockSystemMessage.from("Bedrock"));

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(messages, null);

        // Only system messages extracted
        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("System");
        assertThat(result.get(1).text()).isEqualTo("Bedrock");
    }

    // === Order Preservation Tests ===

    @Test
    void should_preserve_order_of_system_messages() {
        List<ChatMessage> messages = Arrays.asList(
                BedrockSystemMessage.builder()
                        .addText("Bedrock 1")
                        .addTextWithCachePoint("Bedrock 2")
                        .build(),
                SystemMessage.from("Core"),
                BedrockSystemMessage.from("Bedrock 3"));

        List<SystemContentBlock> result = extractor.testExtractSystemMessages(messages, null);

        assertThat(result).hasSize(5); // B1, B2, cache, Core, B3
        assertThat(result.get(0).text()).isEqualTo("Bedrock 1");
        assertThat(result.get(1).text()).isEqualTo("Bedrock 2");
        assertThat(result.get(2).cachePoint()).isNotNull();
        assertThat(result.get(3).text()).isEqualTo("Core");
        assertThat(result.get(4).text()).isEqualTo("Bedrock 3");
    }

    // === Empty List Tests ===

    @Test
    void should_handle_empty_messages_list() {
        List<SystemContentBlock> result = extractor.testExtractSystemMessages(List.of(), null);
        assertThat(result).isEmpty();
    }
}
