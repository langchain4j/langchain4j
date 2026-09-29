package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.tool.search.decision.DecisionModelToolSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ToolProvider} that passes on only the tools of another tool provider that are relevant to the conversation,
 * as decided by a {@link DecisionModel}. This keeps requests to the LLM small when a tool provider offers many tools,
 * for example an MCP server:
 * <pre>{@code
 * Assistant assistant = AiServices.builder(Assistant.class)
 *         .chatModel(chatModel)
 *         .toolProvider(DecisionModelFilteringToolProvider.builder()
 *                 .toolProvider(mcpToolProvider)
 *                 .decisionModel(decisionModel)
 *                 .maxResults(5)
 *                 .build())
 *         .build();
 * }</pre>
 * Unlike a {@link dev.langchain4j.service.tool.search.ToolSearchStrategy}, the tools are selected before the first
 * LLM call, so no tool search round trip is needed. By default, the selection is based on the user message only; with
 * {@link Builder#maxMessages(Integer)}, previous messages of the conversation are also taken into account, which helps
 * with follow-up messages such as "do the same for Berlin".
 * <p>
 * If the user message has no text, all tools are passed on. If the decision model fails, the {@link FallbackStrategy}
 * applies: by default, all tools are passed on and a warning is logged.
 *
 * @see dev.langchain4j.service.tool.search.decision.DecisionModelToolSearchStrategy
 * @since 1.21.0
 */
@Experimental
public class DecisionModelFilteringToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelFilteringToolProvider.class);

    private static final int DEFAULT_MAX_MESSAGES = 1;
    private static final int MAX_CACHED_SELECTIONS = 100;

    /**
     * What the tool provider does when the decision model fails.
     */
    public enum FallbackStrategy {

        /**
         * Pass on all tools of the wrapped tool provider, and log a warning.
         */
        ALL_TOOLS,

        /**
         * Pass on only the tools configured with {@link Builder#alwaysInclude(String...)}, and log a warning.
         */
        NO_TOOLS,

        /**
         * Fail the request with the error of the decision model.
         */
        FAIL
    }

    private final ToolProvider toolProvider;
    private final DecisionModelToolSelector selector;
    private final Set<String> alwaysIncludedTools;
    private final int maxMessages;
    private final FallbackStrategy fallbackStrategy;
    private final Map<List<Object>, Set<String>> cache;

    public DecisionModelFilteringToolProvider(ToolProvider toolProvider, DecisionModel decisionModel) {
        this(builder().toolProvider(toolProvider).decisionModel(decisionModel));
    }

    protected DecisionModelFilteringToolProvider(Builder builder) {
        this.toolProvider = ensureNotNull(builder.toolProvider, "toolProvider");
        this.selector = new DecisionModelToolSelector(
                builder.decisionModel,
                builder.question,
                builder.maxResults,
                builder.minProbability,
                builder.maxToolsPerRequest);
        this.alwaysIncludedTools = Set.copyOf(builder.alwaysIncludedTools);
        this.maxMessages = ensureGreaterThanZero(getOrDefault(builder.maxMessages, DEFAULT_MAX_MESSAGES), "maxMessages");
        this.fallbackStrategy = getOrDefault(builder.fallbackStrategy, FallbackStrategy.ALL_TOOLS);
        this.cache = getOrDefault(builder.cacheSelections, true)
                ? Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<List<Object>, Set<String>> eldest) {
                        return size() > MAX_CACHED_SELECTIONS;
                    }
                })
                : null;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult result = toolProvider.provideTools(request);
        if (result == null || result.aiServiceTools().isEmpty()) {
            return result;
        }
        Object input = input(request);
        if (input == null) {
            return result;
        }

        List<ToolSpecification> candidates = result.aiServiceTools().stream()
                .map(AiServiceTool::toolSpecification)
                .filter(tool -> !alwaysIncludedTools.contains(tool.name()))
                .toList();
        Set<String> selected = new HashSet<>(alwaysIncludedTools);
        if (!candidates.isEmpty()) {
            Set<String> selectedCandidates = select(input, candidates);
            if (selectedCandidates == null) {
                return result;
            }
            selected.addAll(selectedCandidates);
        }
        return new ToolProviderResult(result.aiServiceTools().stream()
                .filter(tool -> selected.contains(tool.name()))
                .toList());
    }

    /**
     * Returns the names of the selected tools, or {@code null} if all tools should be passed on.
     */
    private Set<String> select(Object input, List<ToolSpecification> candidates) {
        List<Object> cacheKey = cache == null ? null : List.of(input, candidates);
        if (cacheKey != null) {
            Set<String> cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        Set<String> selected;
        try {
            selected = Set.copyOf(selector.select(input, candidates));
        } catch (RuntimeException e) {
            return switch (fallbackStrategy) {
                case ALL_TOOLS -> {
                    log.warn("Failed to select tools, all tools will be passed on", e);
                    yield null;
                }
                case NO_TOOLS -> {
                    log.warn("Failed to select tools, only the always included tools will be passed on", e);
                    yield Set.of();
                }
                case FAIL -> throw e;
            };
        }
        if (cacheKey != null) {
            cache.put(cacheKey, selected);
        }
        return selected;
    }

    /**
     * The text of the user message or, with {@code maxMessages > 1}, the last messages of the conversation. Returns
     * {@code null} if the user message has no text.
     */
    private Object input(ToolProviderRequest request) {
        String userMessage = text(request.userMessage());
        if (userMessage.isBlank()) {
            return null;
        }
        if (maxMessages == 1) {
            return userMessage;
        }
        List<Map<String, String>> conversation = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            if (message instanceof UserMessage user && !text(user).isBlank()) {
                conversation.add(Map.of("role", "user", "text", text(user)));
            } else if (message instanceof AiMessage ai && ai.text() != null && !ai.text().isBlank()) {
                conversation.add(Map.of("role", "assistant", "text", ai.text()));
            }
        }
        Map<String, String> current = Map.of("role", "user", "text", userMessage);
        if (conversation.isEmpty() || !conversation.get(conversation.size() - 1).equals(current)) {
            conversation.add(current);
        }
        return conversation.subList(Math.max(0, conversation.size() - maxMessages), conversation.size());
    }

    @Override
    public boolean isDynamic() {
        return toolProvider.isDynamic();
    }

    private static String text(UserMessage userMessage) {
        if (userMessage == null) {
            return "";
        }
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(joining("\n"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolProvider toolProvider;
        private DecisionModel decisionModel;
        private String question;
        private Integer maxResults;
        private Double minProbability;
        private Integer maxToolsPerRequest;
        private final Set<String> alwaysIncludedTools = new HashSet<>();
        private Integer maxMessages;
        private FallbackStrategy fallbackStrategy;
        private Boolean cacheSelections;

        /**
         * Sets the tool provider whose tools are filtered. Required.
         */
        public Builder toolProvider(ToolProvider toolProvider) {
            this.toolProvider = toolProvider;
            return this;
        }

        /**
         * Sets the decision model that decides which tools are relevant. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the yes/no question asked for each tool, followed by the name and description of the tool.
         * <p>
         * Default value is {@value DecisionModelToolSelector#DEFAULT_QUESTION}.
         */
        public Builder question(String question) {
            this.question = question;
            return this;
        }

        /**
         * Sets the maximum number of selected tools, in addition to the always included ones.
         * <p>
         * Default value is {@value DecisionModelToolSelector#DEFAULT_MAX_RESULTS}.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum probability of "yes" for a tool to be selected.
         * <p>
         * Default value is {@value DecisionModelToolSelector#DEFAULT_MIN_PROBABILITY}.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        /**
         * Sets the maximum number of tools evaluated in a single request to the decision model, for when the tools
         * together exceed the input size accepted by the decision model.
         * <p>
         * By default, all tools are evaluated in a single request.
         */
        public Builder maxToolsPerRequest(Integer maxToolsPerRequest) {
            this.maxToolsPerRequest = maxToolsPerRequest;
            return this;
        }

        /**
         * Sets tools that are always passed on, without asking the decision model.
         */
        public Builder alwaysInclude(String... toolNames) {
            return alwaysInclude(List.of(toolNames));
        }

        /**
         * Sets tools that are always passed on, without asking the decision model.
         */
        public Builder alwaysInclude(Collection<String> toolNames) {
            this.alwaysIncludedTools.clear();
            if (toolNames != null) {
                this.alwaysIncludedTools.addAll(toolNames);
            }
            return this;
        }

        /**
         * Sets how many of the last messages of the conversation (user and assistant messages with text, the last
         * one being the user message) the decision model receives.
         * <p>
         * Default value is {@value DecisionModelFilteringToolProvider#DEFAULT_MAX_MESSAGES}: only the user message.
         */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        /**
         * Sets what happens when the decision model fails.
         * <p>
         * Default value is {@link FallbackStrategy#ALL_TOOLS}.
         */
        public Builder fallbackStrategy(FallbackStrategy fallbackStrategy) {
            this.fallbackStrategy = fallbackStrategy;
            return this;
        }

        /**
         * Sets whether the selection is reused when the same conversation and tools are seen again, for example when
         * the wrapped tool provider is {@link ToolProvider#isDynamic() dynamic} and is called before each LLM call of
         * the tool-calling loop. The last 100 selections are kept.
         * <p>
         * Default value is {@code true}.
         */
        public Builder cacheSelections(Boolean cacheSelections) {
            this.cacheSelections = cacheSelections;
            return this;
        }

        public DecisionModelFilteringToolProvider build() {
            return new DecisionModelFilteringToolProvider(this);
        }
    }
}
