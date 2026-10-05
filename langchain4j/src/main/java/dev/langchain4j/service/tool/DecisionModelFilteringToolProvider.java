package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.tool.search.decision.DecisionModelToolSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
 *                 .build())
 *         .build();
 * }</pre>
 * Unlike a {@link dev.langchain4j.service.tool.search.ToolSearchStrategy}, the tools are selected before the first
 * LLM call, so no tool search round trip is needed. By default, every tool whose probability of being useful reaches
 * the minimum probability is passed on, and the selection is based on the last 3 messages of the conversation (see
 * {@link Builder#maxMessages(Integer)}), which helps with follow-up messages such as "do the same for Berlin".
 * <p>
 * Only the tools of the wrapped tool provider are filtered: tools configured directly on the AI Service, tools with
 * the {@link dev.langchain4j.agent.tool.SearchBehavior#ALWAYS_VISIBLE} search behavior and the tools configured with
 * {@link Builder#alwaysInclude(String...)} are always passed on. Tools that were already called in the conversation are also always passed on, since some LLM providers
 * reject requests whose messages contain calls to tools that are not in the request. This is similar to a
 * {@link dev.langchain4j.service.tool.search.ToolSearchStrategy}, whose previously found tools stay available. The
 * previous messages, used both for this and for {@link Builder#maxMessages(Integer)}, are only known if the caller
 * passes them in {@link ToolProviderRequest#messages()}, as LangChain4j AI Services do.
 * <p>
 * If the wrapped tool provider is {@link ToolProvider#isDynamic() dynamic}, the tools are selected again, with a call
 * to the decision model, before each LLM call of the tool-calling loop. Since the messages sent to the decision model
 * usually do not change within a tool-calling loop, this only helps if the tools of the wrapped provider change.
 * <p>
 * The tools are selected for the user message as the user sent it: in AI Services, before retrieved content (RAG) and
 * output format instructions were added to it (see {@link InvocationContext#originalUserMessage()}). Otherwise, the
 * retrieved documents rather than the question would decide which tools are selected. The previous messages are sent
 * as they are stored in the chat memory, which by default includes the retrieved content.
 * <p>
 * If the user message has no text, all tools are passed on. If the decision model fails, the
 * {@link FallbackStrategy} applies: by default, all tools are passed on and a warning is logged.
 *
 * @see dev.langchain4j.service.tool.search.decision.DecisionModelToolSearchStrategy
 * @since 1.21.0
 */
@Experimental
public class DecisionModelFilteringToolProvider implements ToolProvider {

    /**
     * The default template of the question asked for each tool:
     * {@code "Would this tool help to handle the request?\nTool: {{name}}\nDescription: {{description}}"}.
     */
    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE = DecisionModelToolSelector.DEFAULT_QUESTION_TEMPLATE;


    private static final Logger log = LoggerFactory.getLogger(DecisionModelFilteringToolProvider.class);

    private static final int DEFAULT_MAX_MESSAGES = 3;

    /**
     * What the tool provider does when the decision model fails.
     */
    public enum FallbackStrategy {

        /**
         * Pass on all tools of the wrapped tool provider, and log a warning.
         */
        ALL_TOOLS,

        /**
         * Pass on only the tools configured with {@link Builder#alwaysInclude(String...)} and the tools already called in
         * the conversation, and log a warning.
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

    public DecisionModelFilteringToolProvider(ToolProvider toolProvider, DecisionModel decisionModel) {
        this(builder().toolProvider(toolProvider).decisionModel(decisionModel));
    }

    protected DecisionModelFilteringToolProvider(Builder builder) {
        this.toolProvider = ensureNotNull(builder.toolProvider, "toolProvider");
        this.selector = new DecisionModelToolSelector(
                builder.decisionModel,
                builder.questionTemplate,
                getOrDefault(builder.maxResults, Integer.MAX_VALUE),
                builder.minProbability,
                builder.maxToolsPerDecisionRequest);
        this.alwaysIncludedTools = Set.copyOf(builder.alwaysIncludedTools);
        this.maxMessages = ensureGreaterThanZero(getOrDefault(builder.maxMessages, DEFAULT_MAX_MESSAGES), "maxMessages");
        this.fallbackStrategy = getOrDefault(builder.fallbackStrategy, FallbackStrategy.ALL_TOOLS);
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

        Set<String> selected = new HashSet<>(alwaysIncludedTools);
        selected.addAll(calledTools(request.messages()));
        result.aiServiceTools().stream()
                .map(AiServiceTool::toolSpecification)
                .filter(tool -> tool.metadata().get(METADATA_SEARCH_BEHAVIOR) == ALWAYS_VISIBLE)
                .forEach(tool -> selected.add(tool.name()));
        List<ToolSpecification> candidates = result.aiServiceTools().stream()
                .map(AiServiceTool::toolSpecification)
                .filter(tool -> !selected.contains(tool.name()))
                .toList();
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
        try {
            return Set.copyOf(selector.select(input, candidates));
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
    }

    private static Set<String> calledTools(List<ChatMessage> messages) {
        Set<String> calledTools = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                aiMessage.toolExecutionRequests().forEach(toolCall -> calledTools.add(toolCall.name()));
            }
        }
        return calledTools;
    }

    /**
     * The text of the user message or, with {@code maxMessages > 1}, the last messages of the conversation. Returns
     * {@code null} if the user message has no text.
     */
    private Object input(ToolProviderRequest request) {
        UserMessage userMessage = originalUserMessage(request);
        if (userMessage == null || !DecisionModelInputUtils.hasText(userMessage)) {
            return null;
        }
        String text = DecisionModelInputUtils.text(userMessage);
        if (maxMessages == 1) {
            return text;
        }
        List<Map<String, String>> conversation = new ArrayList<>(DecisionModelInputUtils.messages(request.messages()));
        Map<String, String> current = Map.of("role", "user", "text", text);
        if (!conversation.isEmpty() && isCurrentUserMessage(conversation.get(conversation.size() - 1), request, text)) {
            conversation.set(conversation.size() - 1, current);
        } else {
            conversation.add(current);
        }
        return Map.of(
                "messages",
                conversation.subList(Math.max(0, conversation.size() - maxMessages), conversation.size()));
    }

    /**
     * The user message before retrieved content was added to it (see {@link InvocationContext#originalUserMessage()}),
     * if known, otherwise the user message sent to the LLM.
     */
    private static UserMessage originalUserMessage(ToolProviderRequest request) {
        InvocationContext invocationContext = request.invocationContext();
        if (invocationContext != null
                && invocationContext.originalUserMessage() != null
                && DecisionModelInputUtils.hasText(invocationContext.originalUserMessage())) {
            return invocationContext.originalUserMessage();
        }
        return request.userMessage();
    }

    /**
     * Whether the message is the current user message, as sent to the LLM or before retrieved content was added.
     */
    private static boolean isCurrentUserMessage(Map<String, String> message, ToolProviderRequest request, String text) {
        if (!"user".equals(message.get("role"))) {
            return false;
        }
        return message.get("text").equals(text)
                || (request.userMessage() != null
                        && message.get("text").equals(DecisionModelInputUtils.text(request.userMessage())));
    }

    @Override
    public boolean isDynamic() {
        return toolProvider.isDynamic();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolProvider toolProvider;
        private DecisionModel decisionModel;
        private PromptTemplate questionTemplate;
        private Integer maxResults;
        private Double minProbability;
        private Integer maxToolsPerDecisionRequest;
        private final Set<String> alwaysIncludedTools = new HashSet<>();
        private Integer maxMessages;
        private FallbackStrategy fallbackStrategy;

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
         * Sets the template of the yes/no question asked for each tool, which must contain the {@code {{name}}}
         * variable (the name of the tool) and can contain {@code {{description}}} (its description, empty if it has
         * none).
         * <p>
         * Default value is {@link DecisionModelFilteringToolProvider#DEFAULT_QUESTION_TEMPLATE}.
         */
        public Builder questionTemplate(PromptTemplate questionTemplate) {
            this.questionTemplate = questionTemplate;
            return this;
        }

        /**
         * Sets the maximum number of selected tools, in addition to the always included ones. The tools with the
         * highest probabilities are kept.
         * <p>
         * By default, there is no limit: every tool whose probability reaches the minimum probability is passed on,
         * since a tool that is not passed on cannot be used at all in this request.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum probability of "yes" for a tool to be selected.
         * <p>
         * Default value is 0.5.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        /**
         * Sets the maximum number of tools evaluated in a single request to the decision model, for when the tools
         * together exceed the input size accepted by the decision model.
         * <p>
         * By default, all tools are evaluated in a single request. This does not limit the number of tools passed on
         * to the LLM, see {@link #maxResults(Integer)}.
         */
        public Builder maxToolsPerDecisionRequest(Integer maxToolsPerDecisionRequest) {
            this.maxToolsPerDecisionRequest = maxToolsPerDecisionRequest;
            return this;
        }

        /**
         * Sets tools that are always passed on, without asking the decision model. Replaces the previously set tools.
         */
        public Builder alwaysInclude(String... toolNames) {
            return alwaysInclude(List.of(toolNames));
        }

        /**
         * Sets tools that are always passed on, without asking the decision model. Replaces the previously set tools.
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
         * one being the user message) the decision model receives. System messages, tool calls and tool results are
         * never sent, and do not count.
         * <p>
         * Default value is {@value DecisionModelFilteringToolProvider#DEFAULT_MAX_MESSAGES}. More messages can make
         * an older topic of the conversation outweigh the user message.
         * Previous messages are only available if the AI Service passes them in {@link ToolProviderRequest#messages()}.
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

        public DecisionModelFilteringToolProvider build() {
            return new DecisionModelFilteringToolProvider(this);
        }
    }
}
