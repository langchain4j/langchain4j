package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.retainKeys;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTool;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.anthropic.internal.api.*;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AnthropicMapperTest {

    static final String DICE_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    @ParameterizedTest
    @MethodSource
    void test_toAnthropicMessages(List<ChatMessage> messages, List<AnthropicMessage> expectedAnthropicMessages) {
        // when
        List<AnthropicMessage> anthropicMessages = toAnthropicMessages(messages);

        // then
        assertThat(anthropicMessages).containsExactlyElementsOf(expectedAnthropicMessages);
    }

    static Stream<Arguments> test_toAnthropicMessages() {
        return Stream.of(
                Arguments.of(
                        singletonList(UserMessage.from("Hello")),
                        singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))))),
                Arguments.of(
                        asList(SystemMessage.from("Ignored"), UserMessage.from("Hello")),
                        singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))))),
                Arguments.of(
                        asList(UserMessage.from("Hello"), SystemMessage.from("Ignored")),
                        singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))))),
                Arguments.of(
                        asList(UserMessage.from("Hello"), AiMessage.from("Hi"), UserMessage.from("How are you?")),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))),
                                new AnthropicMessage(ASSISTANT, singletonList(new AnthropicTextContent("Hi"))),
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How are you?"))))),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2?"),
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("12345")
                                        .name("calculator")
                                        .arguments("{\"first\": 2, \"second\": 2}")
                                        .build()),
                                ToolExecutionResultMessage.from("12345", "calculator", "4")),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How much is 2+2?"))),
                                new AnthropicMessage(
                                        ASSISTANT,
                                        singletonList(AnthropicToolUseContent.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 2), entry("second", 2)))
                                                .build())),
                                new AnthropicMessage(
                                        USER, singletonList(new AnthropicToolResultContent("12345", "4", null))))),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2?"),
                                new AiMessage(
                                        "<thinking>I need to use the calculator tool</thinking>",
                                        singletonList(ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .arguments("{\"first\": 2, \"second\": 2}")
                                                .build())),
                                ToolExecutionResultMessage.from("12345", "calculator", "4")),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How much is 2+2?"))),
                                new AnthropicMessage(
                                        ASSISTANT,
                                        asList(
                                                new AnthropicTextContent(
                                                        "<thinking>I need to use the calculator tool</thinking>"),
                                                AnthropicToolUseContent.builder()
                                                        .id("12345")
                                                        .name("calculator")
                                                        .input(mapOf(entry("first", 2), entry("second", 2)))
                                                        .build())),
                                new AnthropicMessage(
                                        USER, singletonList(new AnthropicToolResultContent("12345", "4", null))))),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2 and 3+3?"),
                                AiMessage.from(
                                        ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .arguments("{\"first\": 2, \"second\": 2}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("67890")
                                                .name("calculator")
                                                .arguments("{\"first\": 3, \"second\": 3}")
                                                .build()),
                                ToolExecutionResultMessage.from("12345", "calculator", "4"),
                                ToolExecutionResultMessage.from("67890", "calculator", "6")),
                        asList(
                                new AnthropicMessage(
                                        USER, singletonList(new AnthropicTextContent("How much is 2+2 and 3+3?"))),
                                new AnthropicMessage(
                                        ASSISTANT,
                                        asList(
                                                AnthropicToolUseContent.builder()
                                                        .id("12345")
                                                        .name("calculator")
                                                        .input(mapOf(entry("first", 2), entry("second", 2)))
                                                        .build(),
                                                AnthropicToolUseContent.builder()
                                                        .id("67890")
                                                        .name("calculator")
                                                        .input(mapOf(entry("first", 3), entry("second", 3)))
                                                        .build())),
                                new AnthropicMessage(
                                        USER,
                                        asList(
                                                new AnthropicToolResultContent("12345", "4", null),
                                                new AnthropicToolResultContent("67890", "6", null))))),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2 and 3+3?"),
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("12345")
                                        .name("calculator")
                                        .arguments("{\"first\": 2, \"second\": 2}")
                                        .build()),
                                ToolExecutionResultMessage.from("12345", "calculator", "4"),
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("67890")
                                        .name("calculator")
                                        .arguments("{\"first\": 3, \"second\": 3}")
                                        .build()),
                                ToolExecutionResultMessage.from("67890", "calculator", "6")),
                        asList(
                                new AnthropicMessage(
                                        USER, singletonList(new AnthropicTextContent("How much is 2+2 and 3+3?"))),
                                new AnthropicMessage(
                                        ASSISTANT,
                                        singletonList(AnthropicToolUseContent.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 2), entry("second", 2)))
                                                .build())),
                                new AnthropicMessage(
                                        USER, singletonList(new AnthropicToolResultContent("12345", "4", null))),
                                new AnthropicMessage(
                                        ASSISTANT,
                                        singletonList(AnthropicToolUseContent.builder()
                                                .id("67890")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 3), entry("second", 3)))
                                                .build())),
                                new AnthropicMessage(
                                        USER, singletonList(new AnthropicToolResultContent("67890", "6", null))))),
                Arguments.of(
                        singletonList(UserMessage.from(ImageContent.from(
                                Image.builder().url(URI.create(DICE_IMAGE_URL)).build()))),
                        singletonList(new AnthropicMessage(
                                USER, singletonList(AnthropicImageContent.fromUrl(DICE_IMAGE_URL))))),
                Arguments.of(
                        singletonList(UserMessage.from(ImageContent.from(Image.builder()
                                .base64Data("base64data")
                                .mimeType("image/jpeg")
                                .build()))),
                        singletonList(new AnthropicMessage(
                                USER, singletonList(AnthropicImageContent.fromBase64("image/jpeg", "base64data"))))),
                Arguments.of(
                        singletonList(UserMessage.from(
                                TextContent.from("Describe this image"),
                                ImageContent.from(Image.builder()
                                        .url(URI.create(DICE_IMAGE_URL))
                                        .build()))),
                        singletonList(new AnthropicMessage(
                                USER,
                                asList(
                                        new AnthropicTextContent("Describe this image"),
                                        AnthropicImageContent.fromUrl(DICE_IMAGE_URL))))),
                Arguments.of(
                        singletonList(
                                UserMessage.from(PdfFileContent.from(URI.create("https://example.com/document.pdf")))),
                        singletonList(new AnthropicMessage(
                                USER, singletonList(AnthropicPdfContent.fromUrl("https://example.com/document.pdf"))))),
                Arguments.of(
                        singletonList(UserMessage.from(PdfFileContent.from("base64data", "application/pdf"))),
                        singletonList(new AnthropicMessage(
                                USER, singletonList(AnthropicPdfContent.fromBase64("application/pdf", "base64data"))))),
                Arguments.of(
                        singletonList(UserMessage.from(
                                TextContent.from("Analyze this document"),
                                PdfFileContent.from(URI.create("https://example.com/document.pdf")))),
                        singletonList(new AnthropicMessage(
                                USER,
                                asList(
                                        new AnthropicTextContent("Analyze this document"),
                                        AnthropicPdfContent.fromUrl("https://example.com/document.pdf"))))));
    }

    @ParameterizedTest
    @MethodSource
    void test_toAnthropicTool(ToolSpecification toolSpecification, AnthropicTool expectedAnthropicTool) {

        // when
        AnthropicTool anthropicTool = toAnthropicTool(toolSpecification, AnthropicCacheType.NO_CACHE);

        // then
        assertThat(anthropicTool).isEqualTo(expectedAnthropicTool);
    }

    static Stream<Arguments> test_toAnthropicTool() {
        return Stream.of(
                Arguments.of(
                        ToolSpecification.builder()
                                .name("name")
                                .description("description")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("parameter")
                                        .required("parameter")
                                        .build())
                                .build(),
                        AnthropicTool.builder()
                                .name("name")
                                .description("description")
                                .inputSchema(AnthropicToolSchema.builder()
                                        .properties(singletonMap("parameter", singletonMap("type", "string")))
                                        .required(singletonList("parameter"))
                                        .build())
                                .build()),
                Arguments.of(
                        ToolSpecification.builder().name("tool").build(),
                        AnthropicTool.builder()
                                .name("tool")
                                .inputSchema(AnthropicToolSchema.builder()
                                        .properties(emptyMap())
                                        .required(emptyList())
                                        .build())
                                .build()));
    }

    @Test
    void should_retain_keys() {
        assertThat(retainKeys(Map.of(), Set.of())).isEqualTo(Map.of());
        assertThat(retainKeys(Map.of("one", "one"), Set.of("one"))).isEqualTo(Map.of("one", "one"));
        assertThat(retainKeys(Map.of("one", "one"), Set.of("two"))).isEqualTo(Map.of());
        assertThat(retainKeys(Map.of("one", "one", "two", "two"), Set.of("one")))
                .isEqualTo(Map.of("one", "one"));
    }

    @SafeVarargs
    private static <K, V> Map<K, V> mapOf(Map.Entry<K, V>... entries) {
        return Stream.of(entries).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }
}
