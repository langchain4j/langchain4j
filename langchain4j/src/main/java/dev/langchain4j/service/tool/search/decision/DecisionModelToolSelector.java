package dev.langchain4j.service.tool.search.decision;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selects the tools relevant to a request with a {@link DecisionModel}: one yes/no question per tool, answered in
 * one request (or in batches, if configured), keeping the tools whose probability of "yes" reaches the minimum, most relevant first.
 */
@Internal
public final class DecisionModelToolSelector {

    public static final String DEFAULT_QUESTION = "Would this tool help to handle the request?";
    public static final int DEFAULT_MAX_RESULTS = 5;
    public static final double DEFAULT_MIN_PROBABILITY = 0.5;

    private final DecisionModel decisionModel;
    private final String question;
    private final int maxResults;
    private final double minProbability;
    private final int maxToolsPerRequest;

    public DecisionModelToolSelector(
            DecisionModel decisionModel,
            String question,
            Integer maxResults,
            Double minProbability,
            Integer maxToolsPerRequest) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        this.question = ensureNotBlank(getOrDefault(question, DEFAULT_QUESTION), "question");
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, DEFAULT_MAX_RESULTS), "maxResults");
        this.minProbability =
                ensureBetween(getOrDefault(minProbability, DEFAULT_MIN_PROBABILITY), 0, 1, "minProbability");
        this.maxToolsPerRequest = maxToolsPerRequest == null
                ? Integer.MAX_VALUE
                : ensureGreaterThanZero(maxToolsPerRequest, "maxToolsPerRequest");
    }

    public List<String> select(Object input, List<ToolSpecification> tools) {
        List<ScoredTool> scoredTools = new ArrayList<>();
        for (int start = 0; start < tools.size(); ) {
            List<ToolSpecification> batch = tools.subList(start, start + Math.min(maxToolsPerRequest, tools.size() - start));
            start += batch.size();
            DecisionRequest.Builder decisionRequest = DecisionRequest.builder().input(input);
            for (int i = 0; i < batch.size(); i++) {
                decisionRequest.question("tool" + i, YesNoQuestion.of(question + "\n" + describe(batch.get(i))));
            }
            DecisionResponse response = decisionModel.decide(decisionRequest.build());
            for (int i = 0; i < batch.size(); i++) {
                double probability = response.yesNo("tool" + i).probability();
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

    private static String describe(ToolSpecification tool) {
        return isNullOrBlank(tool.description()) ? "Tool: " + tool.name() : "Tool: " + tool.name() + ": " + tool.description();
    }

    private record ScoredTool(String name, double probability) {}
}
