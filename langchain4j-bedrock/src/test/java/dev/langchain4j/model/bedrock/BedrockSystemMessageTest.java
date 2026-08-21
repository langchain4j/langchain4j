package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockSystemMessageTest {

    // === Creation Tests ===

    @Test
    void should_create_message_with_single_text() {
        BedrockSystemMessage msg = BedrockSystemMessage.from("Hello");

        assertThat(msg.contents()).hasSize(1);
        assertThat(msg.hasSingleText()).isTrue();
        assertThat(msg.singleText()).isEqualTo("Hello");
        assertThat(msg.type()).isEqualTo(ChatMessageType.SYSTEM);
    }

    @Test
    void should_create_message_with_builder() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("First part")
                .addTextWithCachePoint("Second part with cache")
                .addText("Third part")
                .build();

        assertThat(msg.contents()).hasSize(3);
        assertThat(msg.hasSingleText()).isFalse();
    }

    @Test
    void should_create_message_from_content_list() {
        List<BedrockSystemContent> contents = Arrays.asList(
                BedrockSystemTextContent.from("First"), BedrockSystemTextContent.withCachePoint("Second"));

        BedrockSystemMessage msg = BedrockSystemMessage.from(contents);

        assertThat(msg.contents()).hasSize(2);
    }

    @Test
    void should_create_message_from_core_system_message() {
        SystemMessage coreMsg = SystemMessage.from("Core message");
        BedrockSystemMessage bedrockMsg = BedrockSystemMessage.from(coreMsg);

        assertThat(bedrockMsg.singleText()).isEqualTo("Core message");
        assertThat(bedrockMsg.hasSingleText()).isTrue();
    }

    // === Text Extraction Tests ===

    @Test
    void should_return_combined_text() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("Part 1")
                .addTextWithCachePoint("Part 2")
                .addText("Part 3")
                .build();

        assertThat(msg.text()).isEqualTo("Part 1\n\nPart 2\n\nPart 3");
    }

    @Test
    void should_throw_when_getting_single_text_from_multiple_blocks() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("First")
                .addText("Second")
                .build();

        assertThatThrownBy(msg::singleText)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected single text content");
    }

    // === Validation Tests ===

    @Test
    void should_throw_for_empty_contents() {
        assertThatThrownBy(() -> BedrockSystemMessage.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_exceeding_max_content_blocks() {
        BedrockSystemMessage.Builder builder = BedrockSystemMessage.builder();
        for (int i = 0; i < BedrockSystemMessage.MAX_CONTENT_BLOCKS; i++) {
            builder.addText("Block " + i);
        }

        // Adding one more should fail
        assertThatThrownBy(() -> builder.addText("One too many"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum");
    }

    @Test
    void should_accept_max_content_blocks() {
        BedrockSystemMessage.Builder builder = BedrockSystemMessage.builder();
        for (int i = 0; i < BedrockSystemMessage.MAX_CONTENT_BLOCKS; i++) {
            builder.addText("Block " + i);
        }

        BedrockSystemMessage msg = builder.build();
        assertThat(msg.contents()).hasSize(BedrockSystemMessage.MAX_CONTENT_BLOCKS);
    }

    @Test
    void should_throw_when_adding_null_content() {
        assertThatThrownBy(() -> BedrockSystemMessage.builder().addContent(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_contents_list_has_null_item() {
        List<BedrockSystemContent> contentsWithNull = new ArrayList<>();
        contentsWithNull.add(BedrockSystemTextContent.from("valid"));
        contentsWithNull.add(null);

        assertThatThrownBy(() -> BedrockSystemMessage.builder().contents(contentsWithNull))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_contents_list_is_empty() {
        List<BedrockSystemContent> emptyList = new ArrayList<>();

        assertThatThrownBy(() -> BedrockSystemMessage.builder().contents(emptyList))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_contents_list_exceeds_max() {
        List<BedrockSystemContent> tooManyContents = new ArrayList<>();
        for (int i = 0; i <= BedrockSystemMessage.MAX_CONTENT_BLOCKS; i++) {
            tooManyContents.add(BedrockSystemTextContent.from("Block " + i));
        }

        assertThatThrownBy(() -> BedrockSystemMessage.builder().contents(tooManyContents))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content block count");
    }

    // === Immutability Tests ===

    @Test
    void should_return_immutable_contents_list() {
        BedrockSystemMessage msg = BedrockSystemMessage.from("test");
        List<BedrockSystemContent> contents = msg.contents();

        assertThatThrownBy(() -> contents.add(BedrockSystemTextContent.from("new")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_not_be_affected_by_external_list_modification() {
        List<BedrockSystemContent> originalContents = new ArrayList<>();
        originalContents.add(BedrockSystemTextContent.from("original"));

        BedrockSystemMessage msg = BedrockSystemMessage.from(originalContents);

        // Modify original list
        originalContents.add(BedrockSystemTextContent.from("added"));

        // Message should still have only one content
        assertThat(msg.contents()).hasSize(1);
    }

    // === Conversion Tests ===

    @Test
    void should_convert_to_core_system_message() {
        BedrockSystemMessage bedrockMsg = BedrockSystemMessage.builder()
                .addText("Part 1")
                .addTextWithCachePoint("Part 2")
                .build();

        SystemMessage coreMsg = bedrockMsg.toSystemMessage();
        assertThat(coreMsg.text()).isEqualTo("Part 1\n\nPart 2");
    }

    @Test
    void should_create_builder_from_existing_message() {
        BedrockSystemMessage original = BedrockSystemMessage.builder()
                .addText("First")
                .addText("Second")
                .build();

        BedrockSystemMessage modified = original.toBuilder().addText("Third").build();

        assertThat(original.contents()).hasSize(2);
        assertThat(modified.contents()).hasSize(3);
    }

    // === Type Tests ===

    @Test
    void should_have_system_type_but_not_be_system_message() {
        BedrockSystemMessage msg = BedrockSystemMessage.from("test");

        assertThat(msg.type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(msg).isNotInstanceOf(SystemMessage.class);
    }

    // === toString Tests ===

    @Test
    void should_have_compact_toString() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("a".repeat(10000))
                .addText("b".repeat(10000))
                .build();

        String str = msg.toString();
        assertThat(str).contains("2 blocks");
        assertThat(str.length()).isLessThan(100); // Compact
    }

    // === Equals, HashCode ===

    @Test
    void should_implement_equals() {
        BedrockSystemMessage msg1 = BedrockSystemMessage.from("test");
        BedrockSystemMessage msg2 = BedrockSystemMessage.from("test");
        BedrockSystemMessage msg3 = BedrockSystemMessage.from("different");

        assertThat(msg1).isEqualTo(msg2);
        assertThat(msg1).isNotEqualTo(msg3);
        assertThat(msg1).isNotEqualTo(SystemMessage.from("test"));
        assertThat(msg1).isNotEqualTo(null);
    }

    @Test
    void should_implement_hashcode() {
        BedrockSystemMessage msg1 = BedrockSystemMessage.from("test");
        BedrockSystemMessage msg2 = BedrockSystemMessage.from("test");

        assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode());
    }

    // === Cache Point Verification Tests ===

    @Test
    void should_preserve_cache_points_in_contents() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("No cache")
                .addTextWithCachePoint("With cache")
                .addText("No cache again")
                .build();

        List<BedrockSystemContent> contents = msg.contents();
        assertThat(((BedrockSystemTextContent) contents.get(0)).hasCachePoint()).isFalse();
        assertThat(((BedrockSystemTextContent) contents.get(1)).hasCachePoint()).isTrue();
        assertThat(((BedrockSystemTextContent) contents.get(2)).hasCachePoint()).isFalse();
    }

    @Test
    void should_report_has_cache_points() {
        BedrockSystemMessage withCachePoints = BedrockSystemMessage.builder()
                .addText("No cache")
                .addTextWithCachePoint("With cache")
                .build();

        BedrockSystemMessage withoutCachePoints = BedrockSystemMessage.builder()
                .addText("No cache 1")
                .addText("No cache 2")
                .build();

        assertThat(withCachePoints.hasCachePoints()).isTrue();
        assertThat(withoutCachePoints.hasCachePoints()).isFalse();
    }

    @Test
    void should_count_cache_points() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("No cache")
                .addTextWithCachePoint("Cache 1")
                .addText("No cache")
                .addTextWithCachePoint("Cache 2")
                .build();

        assertThat(msg.cachePointCount()).isEqualTo(2);
    }

    @Test
    void should_throw_when_exceeding_max_cache_points() {
        BedrockSystemMessage.Builder builder = BedrockSystemMessage.builder();
        for (int i = 0; i <= BedrockSystemMessage.MAX_CACHE_POINTS; i++) {
            builder.addTextWithCachePoint("Cache point " + i);
        }

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum " + BedrockSystemMessage.MAX_CACHE_POINTS + " cache points");
    }

    @Test
    void should_accept_max_cache_points() {
        BedrockSystemMessage.Builder builder = BedrockSystemMessage.builder();
        for (int i = 0; i < BedrockSystemMessage.MAX_CACHE_POINTS; i++) {
            builder.addTextWithCachePoint("Cache point " + i);
        }

        BedrockSystemMessage msg = builder.build();
        assertThat(msg.cachePointCount()).isEqualTo(BedrockSystemMessage.MAX_CACHE_POINTS);
    }

    @Test
    void should_include_cache_point_count_in_toString() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addText("No cache")
                .addTextWithCachePoint("Cache 1")
                .addTextWithCachePoint("Cache 2")
                .build();

        String str = msg.toString();
        assertThat(str).contains("3 blocks");
        assertThat(str).contains("cachePoints = 2");
    }

    @Test
    void should_allow_builder_reuse_after_build() {
        BedrockSystemMessage.Builder builder = BedrockSystemMessage.builder();
        builder.addText("First");
        BedrockSystemMessage msg1 = builder.build();

        // Builder should still work after build
        builder.addText("Second");
        BedrockSystemMessage msg2 = builder.build();

        assertThat(msg1.contents()).hasSize(1);
        assertThat(msg2.contents()).hasSize(2);
    }

    // === Serialization Behavior Documentation ===

    @Test
    void should_document_serialization_limitation() {
        // This test documents the known limitation:
        // BedrockSystemMessage is NOT compatible with ChatMessageSerializer.
        // If serialized via Jackson, it will be deserialized as SystemMessage,
        // losing all granular cache point information.

        BedrockSystemMessage msgWithCachePoints = BedrockSystemMessage.builder()
                .addText("Static instructions")
                .addTextWithCachePoint("Large context to cache")
                .build();

        assertThat(msgWithCachePoints.hasCachePoints()).isTrue();
        assertThat(msgWithCachePoints.cachePointCount()).isEqualTo(1);

        // Converting to SystemMessage (what happens during serialization roundtrip)
        SystemMessage systemMsg = msgWithCachePoints.toSystemMessage();

        // Note: systemMsg is now a core SystemMessage without cache point information
        // This demonstrates the limitation documented in BedrockSystemMessage JavaDoc:
        // "This message type is NOT compatible with standard ChatMessageSerializer"
        assertThat(systemMsg).isInstanceOf(SystemMessage.class);
        assertThat(systemMsg.text()).contains("Static instructions");
        assertThat(systemMsg.text()).contains("Large context to cache");

        // But the cache point metadata is lost
        // (Cache points are specific to BedrockSystemMessage, not SystemMessage)
    }

    // === HIGH: Unicode and Special Character Handling ===

    @Test
    void should_handle_unicode_characters_in_cache_points() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addTextWithCachePoint("Hello 你好 مرحبا здравствуй 🎉")
                .addTextWithCachePoint("Emoji test: 😀 😃 😄 😁")
                .addText("Latin: café, naïve, résumé")
                .build();

        assertThat(msg.contents()).hasSize(3);
        assertThat(((BedrockSystemTextContent) msg.contents().get(0)).text()).contains("你好");
        assertThat(msg.cachePointCount()).isEqualTo(2);
    }

    @Test
    void should_handle_special_characters_in_cache_points() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addTextWithCachePoint("Special: <tag> & \"quote\" 'apostrophe' \n newline \t tab")
                .addTextWithCachePoint("Symbols: @#$%^&*()_+-={}[]|:;<>,.?/")
                .build();

        assertThat(msg.contents()).hasSize(2);
        assertThat(msg.cachePointCount()).isEqualTo(2);
    }

    @Test
    void should_handle_mixed_unicode_and_whitespace() {
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addTextWithCachePoint("  Unicode with leading spaces: 中文  ")
                .addText("\n\nNewlines and unicode: 日本語\n\n")
                .addTextWithCachePoint("	Tabs and unicode: Ελληνικά	")
                .build();

        assertThat(msg.contents()).hasSize(3);
        assertThat(msg.cachePointCount()).isEqualTo(2);
    }

    // === HIGH: Whitespace-Only Content Blocks ===

    @Test
    void should_reject_whitespace_only_text_content() {
        // While blank text is rejected, whitespace-only should be too
        assertThatThrownBy(() -> BedrockSystemMessage.builder()
                        .addText("   ") // Only spaces
                        .build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BedrockSystemMessage.builder()
                        .addText("\t\t\t") // Only tabs
                        .build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BedrockSystemMessage.builder()
                        .addText("\n\n\n") // Only newlines
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_whitespace_only_with_cache_point() {
        assertThatThrownBy(() -> BedrockSystemMessage.builder()
                        .addTextWithCachePoint("   ") // Only spaces
                        .build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BedrockSystemMessage.builder()
                        .addTextWithCachePoint("\n") // Only newline
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_content_with_meaningful_whitespace() {
        // Content with meaningful whitespace (not just whitespace) should be accepted
        BedrockSystemMessage msg = BedrockSystemMessage.builder()
                .addTextWithCachePoint("  Meaningful text with spaces  ")
                .addText("\n\nMultiline text\nwith content\n\n")
                .addTextWithCachePoint("Content\t\twith\t\ttabs")
                .build();

        assertThat(msg.contents()).hasSize(3);
        assertThat(msg.cachePointCount()).isEqualTo(2);
    }
}
