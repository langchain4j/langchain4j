package dev.langchain4j.service.tool.search;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolSearchServiceTest {

    private static final String FOUND_TOOLS_ATTRIBUTE = "found_tools";

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .chatMemoryId("default")
            .build();

    private final ToolSearchService toolSearchService =
            new ToolSearchService(SimpleToolSearchStrategy.builder().build());

    @Test
    void should_make_tool_found_earlier_effective_again() {
        ToolSpecification weather = tool("weather", "Returns the weather forecast");

        ToolServiceContext context =
                adjust(List.of(weather), List.of(userMessage(), toolResultWithFoundTools("weather")));

        assertThat(context.effectiveTools()).contains(weather);
    }

    @Test
    void should_ignore_tool_found_earlier_that_is_no_longer_available() {
        ToolSpecification weather = tool("weather", "Returns the weather forecast");

        // 'time' was found in an earlier invocation and is still referenced by the chat memory,
        // but it is no longer provided for the current invocation
        ToolServiceContext context =
                adjust(List.of(weather), List.of(userMessage(), toolResultWithFoundTools("weather", "time")));

        assertThat(context.effectiveTools())
                .doesNotContainNull()
                .extracting(ToolSpecification::name)
                .doesNotContain("time");
    }

    @Test
    void should_not_fail_when_no_tool_found_earlier_is_available_anymore() {
        ToolServiceContext context = adjust(List.of(), List.of(userMessage(), toolResultWithFoundTools("time")));

        assertThat(context.effectiveTools()).doesNotContainNull();
    }

    @Test
    void should_not_duplicate_tool_found_earlier_that_is_always_visible() {
        ToolSpecification weather = ToolSpecification.builder()
                .name("weather")
                .description("Returns the weather forecast")
                .addMetadata(METADATA_SEARCH_BEHAVIOR, ALWAYS_VISIBLE)
                .build();

        ToolServiceContext context =
                adjust(List.of(weather), List.of(userMessage(), toolResultWithFoundTools("weather")));

        assertThat(context.effectiveTools()).extracting(ToolSpecification::name).containsOnlyOnce("weather");
    }

    private ToolServiceContext adjust(List<ToolSpecification> availableTools, List<ChatMessage> messages) {
        ToolServiceContext context = ToolServiceContext.builder()
                .availableTools(availableTools)
                .effectiveTools(availableTools)
                .build();

        return toolSearchService.adjust(context, messages, INVOCATION_CONTEXT);
    }

    private static ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder().name(name).description(description).build();
    }

    private static UserMessage userMessage() {
        return UserMessage.from("What is the weather?");
    }

    private static ToolExecutionResultMessage toolResultWithFoundTools(String... foundToolNames) {
        return ToolExecutionResultMessage.builder()
                .id("1")
                .toolName("tool_search_tool")
                .text("Tools found: " + String.join(", ", foundToolNames))
                .attributes(Map.of(FOUND_TOOLS_ATTRIBUTE, List.of(foundToolNames)))
                .build();
    }
}
