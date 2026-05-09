package dev.langchain4j.service.tool.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that per-tool invocation limits interact correctly with the tool-search feature:
 * exhausted tools must never be surfaced to the tool-search strategy or re-added to the
 * effective tool set via {@code FOUND_TOOLS_ATTRIBUTE}.
 */
class ToolSearchServiceExhaustionTest {

    private static final String FOUND_TOOLS_ATTRIBUTE = "found_tools";

    @Test
    void refreshSearchExecutors_should_rebuild_searchable_pool_without_exhausted_tools() {
        ToolSpecification toolA = tool("toolA");
        ToolSpecification toolB = tool("toolB");
        ToolSpecification toolC = tool("toolC");
        CapturingToolSearchStrategy strategy = new CapturingToolSearchStrategy();
        ToolSearchService service = new ToolSearchService(strategy);
        InvocationContext invocationContext = InvocationContext.builder().build();

        // Initial adjust produces the executor with the full pool.
        ToolServiceContext adjusted = service.adjust(ctx(List.of(toolA, toolB, toolC)), List.of(), invocationContext);

        // Now toolB has become exhausted mid-run.
        ToolServiceContext refreshed = service.refreshSearchExecutors(adjusted, invocationContext, Set.of("toolB"));

        ToolExecutor searchExecutor = refreshed.toolExecutors().get(strategy.searchTool.name());
        searchExecutor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name(strategy.searchTool.name())
                        .arguments("{}")
                        .build(),
                invocationContext);

        assertThat(strategy.lastRequest.searchableTools())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("toolA", "toolC")
                .doesNotContain("toolB");
    }

    @Test
    void addFoundTools_should_drop_exhausted_names_before_lookup() {
        ToolSpecification toolA = tool("toolA");
        ToolSpecification toolB = tool("toolB");
        // Start with an empty effectiveTools to mirror the post-adjust state where
        // found tools get promoted from availableTools into effectiveTools.
        ToolServiceContext ctx = ctxWithEmptyEffective(List.of(toolA, toolB));

        ToolExecutionResult searchResult = ToolExecutionResult.builder()
                .resultText("found toolA, toolB")
                .attributes(Map.of(FOUND_TOOLS_ATTRIBUTE, List.of("toolA", "toolB")))
                .build();

        ToolServiceContext updated = ToolSearchService.addFoundTools(ctx, List.of(searchResult), Set.of("toolA"));

        assertThat(updated.effectiveTools())
                .extracting(ToolSpecification::name)
                .contains("toolB")
                .doesNotContain("toolA");
    }

    @Test
    void addFoundTools_should_still_throw_for_unknown_non_exhausted_tool_names() {
        // Verifies the original invariant survives the exhaustion filter:
        // a strategy that returns a tool name that is NOT in availableTools AND NOT exhausted
        // is treated as a bug and surfaces as IllegalArgumentException — we only silence
        // names we know to be intentionally excluded.
        ToolSpecification toolA = tool("toolA");
        ToolServiceContext ctx = ctxWithEmptyEffective(List.of(toolA));

        ToolExecutionResult hallucinatedSearchResult = ToolExecutionResult.builder()
                .resultText("found toolGhost")
                .attributes(Map.of(FOUND_TOOLS_ATTRIBUTE, List.of("toolGhost")))
                .build();

        assertThatThrownBy(() -> ToolSearchService.addFoundTools(ctx, List.of(hallucinatedSearchResult), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolGhost");
    }

    @Test
    void addFoundTools_should_throw_for_unknown_name_even_when_other_names_are_exhausted() {
        // Mix: one name is exhausted (legitimately skipped), one is hallucinated (must throw).
        ToolSpecification toolA = tool("toolA");
        ToolSpecification toolB = tool("toolB");
        ToolServiceContext ctx = ctxWithEmptyEffective(List.of(toolA, toolB));

        ToolExecutionResult mixed = ToolExecutionResult.builder()
                .resultText("found toolA, toolGhost")
                .attributes(Map.of(FOUND_TOOLS_ATTRIBUTE, List.of("toolA", "toolGhost")))
                .build();

        assertThatThrownBy(() -> ToolSearchService.addFoundTools(ctx, List.of(mixed), Set.of("toolA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolGhost");
    }

    private static ToolServiceContext ctx(List<ToolSpecification> tools) {
        return ToolServiceContext.builder()
                .effectiveTools(new ArrayList<>(tools))
                .availableTools(new ArrayList<>(tools))
                .toolExecutors(new HashMap<>())
                .build();
    }

    /**
     * Mirrors the state produced by {@link ToolSearchService#adjust} when tool-search is enabled:
     * {@code availableTools} holds all registered tools, but {@code effectiveTools} starts empty
     * (tools are promoted into it only via {@code ALWAYS_VISIBLE}, history replay, or
     * {@link ToolSearchService#addFoundTools}).
     */
    private static ToolServiceContext ctxWithEmptyEffective(List<ToolSpecification> availableTools) {
        return ToolServiceContext.builder()
                .effectiveTools(new ArrayList<>())
                .availableTools(new ArrayList<>(availableTools))
                .toolExecutors(new HashMap<>())
                .build();
    }

    private static ToolSpecification tool(String name) {
        return toolBuilder(name).build();
    }

    private static ToolSpecification.Builder toolBuilder(String name) {
        return ToolSpecification.builder().name(name).description("tool " + name);
    }

    /**
     * Test double that exposes a single tool-search tool and captures the most recent
     * {@link ToolSearchRequest} it receives, so tests can inspect {@code searchableTools}.
     */
    private static class CapturingToolSearchStrategy implements ToolSearchStrategy {

        final ToolSpecification searchTool = ToolSpecification.builder()
                .name("tool_search_tool")
                .description("search for tools")
                .build();

        ToolSearchRequest lastRequest;

        @Override
        public List<ToolSpecification> getToolSearchTools(InvocationContext invocationContext) {
            return List.of(searchTool);
        }

        @Override
        public ToolSearchResult search(ToolSearchRequest toolSearchRequest) {
            this.lastRequest = toolSearchRequest;
            return new ToolSearchResult(List.of(), "no matches");
        }
    }
}
