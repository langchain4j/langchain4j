package dev.langchain4j.service.tool.search.decision;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the tools relevant to a request with a {@link DecisionModel}: one yes/no question per tool, answered in
 * one request (or in batches, if configured), keeping the tools whose probability of "yes" reaches the minimum, most relevant first.
 */
@Internal
public final class DecisionModelToolSelector {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelToolSelector.class);

    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE = PromptTemplate.from(
            "Would this tool help to handle the request?\nTool: {{name}}\nDescription: {{description}}");
    public static final int DEFAULT_MAX_RESULTS = 5;
    public static final double DEFAULT_MIN_PROBABILITY = 0.5;

    private final DecisionModel decisionModel;
    private final PromptTemplate questionTemplate;
    private final int maxResults;
    private final double minProbability;
    private final int maxToolsPerDecisionRequest;

    public DecisionModelToolSelector(
            DecisionModel decisionModel,
            PromptTemplate questionTemplate,
            Integer maxResults,
            Double minProbability,
            Integer maxToolsPerDecisionRequest) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        this.questionTemplate = getOrDefault(questionTemplate, DEFAULT_QUESTION_TEMPLATE);
        if (!this.questionTemplate.template().contains("{{name}}")) {
            throw new IllegalArgumentException("The question template must contain {{name}}, but was: "
                    + this.questionTemplate.template());
        }
        try {
            this.questionTemplate.apply(Map.of("name", "name", "description", "description"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "The question template can only use the {{name}} and {{description}} variables, but was: "
                            + this.questionTemplate.template(),
                    e);
        }
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, DEFAULT_MAX_RESULTS), "maxResults");
        this.minProbability =
                ensureBetween(getOrDefault(minProbability, DEFAULT_MIN_PROBABILITY), 0, 1, "minProbability");
        this.maxToolsPerDecisionRequest = maxToolsPerDecisionRequest == null
                ? Integer.MAX_VALUE
                : ensureGreaterThanZero(maxToolsPerDecisionRequest, "maxToolsPerDecisionRequest");
    }

    /**
     * Selects the tools relevant to the given input: a {@link String} or a {@link Map}.
     */
    public List<String> select(Object input, List<ToolSpecification> tools) {
        List<ScoredTool> scoredTools = new ArrayList<>();
        for (int start = 0; start < tools.size(); ) {
            List<ToolSpecification> batch =
                    tools.subList(start, start + Math.min(maxToolsPerDecisionRequest, tools.size() - start));
            start += batch.size();
            DecisionRequest.Builder decisionRequest = DecisionRequest.builder();
            setInput(decisionRequest, input);
            for (int i = 0; i < batch.size(); i++) {
                decisionRequest.question("tool" + i, YesNoQuestion.of(question(batch.get(i))));
            }
            DecisionResponse response = decisionModel.decide(decisionRequest.build());
            for (int i = 0; i < batch.size(); i++) {
                double probability = response.yesNo("tool" + i).probability();
                log.debug("Tool '{}': probability {}", batch.get(i).name(), probability);
                if (probability >= minProbability) {
                    scoredTools.add(new ScoredTool(batch.get(i).name(), probability));
                }
            }
        }
        return scoredTools.stream()
                .sorted(Comparator.comparingDouble(ScoredTool::probability).reversed())
                .limit(maxResults)
                .map(ScoredTool::name)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static void setInput(DecisionRequest.Builder decisionRequest, Object input) {
        if (input instanceof String text) {
            decisionRequest.input(text);
        } else {
            decisionRequest.input((Map<String, ?>) input);
        }
    }

    private String question(ToolSpecification tool) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", tool.name());
        variables.put("description", isNullOrBlank(tool.description()) ? "" : tool.description());
        return questionTemplate.apply(variables).text().strip();
    }

    private record ScoredTool(String name, double probability) {}
}
