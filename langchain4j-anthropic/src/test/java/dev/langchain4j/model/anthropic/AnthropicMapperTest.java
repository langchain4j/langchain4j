package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toAnthropicTool;
import static dev.langchain4j.model.anthropic.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.AnthropicRole.USER;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

class AnthropicMapperTest {

    @ParameterizedTest
    @MethodSource
    void test_toAnthropicMessages(List<ChatMessage> messages, List<AnthropicMessage> expectedAnthropicMessages) {

        // when
        List<AnthropicMessage> anthropicMessages = toAnthropicMessages(messages);

        //then
        assertThat(anthropicMessages).containsExactlyElementsOf(expectedAnthropicMessages);
    }

    static Stream<Arguments> test_toAnthropicMessages() {
        return Stream.of(
                Arguments.of(
                        singletonList(UserMessage.from("Hello")),
                        singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))))
                ),
                Arguments.of(
                        asList(
                                SystemMessage.from("Ignored"),
                                UserMessage.from("Hello")
                        ),
                        singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))))
                ),
                Arguments.of(
                        asList(
                                UserMessage.from("Hello"),
                                SystemMessage.from("Ignored")
                        ),
                        singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))))
                ),
                Arguments.of(
                        asList(
                                UserMessage.from("Hello"),
                                AiMessage.from("Hi"),
                                UserMessage.from("How are you?")
                        ),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("Hello"))),
                                new AnthropicMessage(ASSISTANT, singletonList(new AnthropicTextContent("Hi"))),
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How are you?")))
                        )
                ),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2?"),
                                AiMessage.from(
                                        ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .arguments("{\"first\": 2, \"second\": 2}")
                                                .build()
                                ),
                                ToolExecutionResultMessage.from("12345", "calculator", "4")
                        ),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How much is 2+2?"))),
                                new AnthropicMessage(ASSISTANT, singletonList(
                                        AnthropicToolUseContent.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 2.0), entry("second", 2.0)))
                                                .build()
                                )),
                                new AnthropicMessage(USER, singletonList(
                                        new AnthropicToolResultContent("12345", "4", null)
                                ))
                        )
                ),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2?"),
                                new AiMessage(
                                        "<thinking>I need to use the calculator tool</thinking>",
                                        singletonList(ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .arguments("{\"first\": 2, \"second\": 2}")
                                                .build())
                                ),
                                ToolExecutionResultMessage.from("12345", "calculator", "4")
                        ),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How much is 2+2?"))),
                                new AnthropicMessage(ASSISTANT, asList(
                                        new AnthropicTextContent("<thinking>I need to use the calculator tool</thinking>"),
                                        AnthropicToolUseContent.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 2.0), entry("second", 2.0)))
                                                .build()
                                )),
                                new AnthropicMessage(USER, singletonList(
                                        new AnthropicToolResultContent("12345", "4", null)
                                ))
                        )
                ),
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
                                                .build()
                                ),
                                ToolExecutionResultMessage.from("12345", "calculator", "4"),
                                ToolExecutionResultMessage.from("67890", "calculator", "6")
                        ),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How much is 2+2 and 3+3?"))),
                                new AnthropicMessage(ASSISTANT, asList(
                                        AnthropicToolUseContent.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 2.0), entry("second", 2.0)))
                                                .build(),
                                        AnthropicToolUseContent.builder()
                                                .id("67890")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 3.0), entry("second", 3.0)))
                                                .build()
                                )),
                                new AnthropicMessage(USER, asList(
                                        new AnthropicToolResultContent("12345", "4", null),
                                        new AnthropicToolResultContent("67890", "6", null)
                                ))
                        )
                ),
                Arguments.of(
                        asList(
                                UserMessage.from("How much is 2+2 and 3+3?"),
                                AiMessage.from(
                                        ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .arguments("{\"first\": 2, \"second\": 2}")
                                                .build()
                                ),
                                ToolExecutionResultMessage.from("12345", "calculator", "4"),
                                AiMessage.from(
                                        ToolExecutionRequest.builder()
                                                .id("67890")
                                                .name("calculator")
                                                .arguments("{\"first\": 3, \"second\": 3}")
                                                .build()
                                ),
                                ToolExecutionResultMessage.from("67890", "calculator", "6")
                        ),
                        asList(
                                new AnthropicMessage(USER, singletonList(new AnthropicTextContent("How much is 2+2 and 3+3?"))),
                                new AnthropicMessage(ASSISTANT, singletonList(
                                        AnthropicToolUseContent.builder()
                                                .id("12345")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 2.0), entry("second", 2.0)))
                                                .build()
                                )),
                                new AnthropicMessage(USER, singletonList(
                                        new AnthropicToolResultContent("12345", "4", null)
                                )),
                                new AnthropicMessage(ASSISTANT, singletonList(
                                        AnthropicToolUseContent.builder()
                                                .id("67890")
                                                .name("calculator")
                                                .input(mapOf(entry("first", 3.0), entry("second", 3.0)))
                                                .build()
                                )),
                                new AnthropicMessage(USER, singletonList(
                                        new AnthropicToolResultContent("67890", "6", null)
                                ))
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void test_toAnthropicTool(ToolSpecification toolSpecification, AnthropicTool expectedAnthropicTool) {

        // when
        AnthropicTool anthropicTool = toAnthropicTool(toolSpecification);

        // then
        assertThat(anthropicTool).isEqualTo(expectedAnthropicTool);
    }

    static Stream<Arguments> test_toAnthropicTool() {
        return Stream.of(
                Arguments.of(
                        ToolSpecification.builder()
                                .name("name")
                                .description("description")
                                .addParameter("parameter", STRING)
                                .build(),
                        AnthropicTool.builder()
                                .name("name")
                                .description("description")
                                .inputSchema(AnthropicToolSchema.builder()
                                        .properties(singletonMap("parameter", singletonMap("type", "string")))
                                        .required(singletonList("parameter"))
                                        .build())
                                .build()
                ),
                Arguments.of(
                        ToolSpecification.builder()
                                .name("tool")
                                .build(),
                        AnthropicTool.builder()
                                .name("tool")
                                .inputSchema(AnthropicToolSchema.builder()
                                        .properties(emptyMap())
                                        .required(emptyList())
                                        .build())
                                .build()
                )
        );
    }

    @SafeVarargs
    private static <K, V> Map<K, V> mapOf(Map.Entry<K, V>... entries) {
        return Stream.of(entries).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }
}