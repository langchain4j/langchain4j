package dev.langchain4j.model.responsibleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.medel.ModelProvider.RESPONSIBLE_AI;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.responsibleai.spi.ResponsibleAiModerationModelBuilderFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponsibleAiModerationModel implements ModerationModel {

    private static final Logger log = LoggerFactory.getLogger(ResponsibleAiModerationModel.class);
    private static final String DEFAULT_BASE_URL = "https://api.responsibleailabs.ai/";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String mode;
    private final List<String> dimensions;
    private final Map<String, Double> weights;
    private final String domain;
    private final Boolean includeExplanations;
    private final Boolean includeIssues;
    private final Boolean includeSuggestions;
    private final Integer maxRetries;
    private final List<ModerationModelListener> listeners;

    public ResponsibleAiModerationModel(ResponsibleAiModerationModelBuilder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        Duration timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        HttpClient client = httpClientBuilder
                .connectTimeout(getOrDefault(builder.connectTimeout, Duration.ofSeconds(15)))
                .readTimeout(timeout)
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient = new LoggingHttpClient(client, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = client;
        }

        this.baseUrl = getOrDefault(builder.baseUrl, DEFAULT_BASE_URL);
        this.apiKey = builder.apiKey;
        this.mode = getOrDefault(builder.mode, "basic");
        this.dimensions = copy(builder.dimensions);
        this.weights = copy(builder.weights);
        this.domain = builder.domain;
        this.includeExplanations = builder.includeExplanations;
        this.includeIssues = builder.includeIssues;
        this.includeSuggestions = builder.includeSuggestions;
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.listeners = copy(builder.listeners);
    }

    @Override
    private ModelProvider provider() {
        return RESPONSIBLE_AI;
    }

    @Override
    public String modelName() {
        return "railscore-" + mode;
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        List<String> texts = moderationRequest.texts();
        List<ResponsibleAiEvalResponse> responses = new ArrayList<>();
        int flaggedIndex = -1;

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            ResponsibleAiEvalRequest requestPayload = ResponsibleAiEvalRequest.builder()
                    .content(text)
                    .mode(mode)
                    .dimensions(dimensions)
                    .weights(weights)
                    .domain(domain)
                    .includeExplanations(includeExplanations)
                    .includeIssues(includeIssues)
                    .includeSuggestions(includeSuggestions)
                    .build();

            ResponsibleAiEvalResponse response =
                    withRetryMappingExceptions(() -> executeRequest(requestPayload), maxRetries);
            responses.add(response);

            if (flaggedIndex == -1) {
                ResponsibleAiEvalResponse.PolicyOutcome policyOutcome = response.getPolicyOutcome();
                if (policyOutcome != null && Boolean.FALSE.equals(policyOutcome.getPassed())) {
                    flaggedIndex = i;
                }
            }
        }

        Moderation moderation =
                flaggedIndex >= 0 ? Moderation.flagged(texts.get(flaggedIndex)) : Moderation.notFlagged();

        Map<String, Object> metadata = new HashMap<>();
        if (!responses.isEmpty()) {
            ResponsibleAiEvalResponse firstResponse = responses.get(0);
            populateMetadata(metadata, "", firstResponse);
            for (int i = 0; i < responses.size(); i++) {
                populateMetadata(metadata, "text." + i + ".", responses.get(i));
            }
        }

        return ModerationResponse.builder()
                .moderation(moderation)
                .metadata(metadata)
                .build();
    }

    private void populateMetadata(Map<String, Object> metadata, String prefix, ResponsibleAiEvalResponse response) {
        ResponsibleAiEvalResponse.PolicyOutcome policyOutcome = response.getPolicyOutcome();
        if (policyOutcome != null) {
            if (policyOutcome.getPassed() != null) {
                metadata.put(prefix + "policy_outcome", policyOutcome.getPassed() ? "PASS" : "FAIL");
                metadata.put(prefix + "policy_outcome.passed", policyOutcome.getPassed());
            }
            if (policyOutcome.getEnforced() != null) {
                metadata.put(prefix + "policy_outcome.enforced", policyOutcome.getEnforced());
            }
            if (policyOutcome.getEnforcement() != null) {
                metadata.put(prefix + "policy_outcome.enforcement", policyOutcome.getEnforcement());
            }
            if (policyOutcome.getThreshold() != null) {
                metadata.put(prefix + "policy_outcome.threshold", policyOutcome.getThreshold());
            }
            if (policyOutcome.getScore() != null) {
                metadata.put(prefix + "policy_outcome.score", policyOutcome.getScore());
            }
        }
        if (response.getFromCache() != null) {
            metadata.put(prefix + "from_cache", response.getFromCache());
        }
        if (response.getCreditsConsumed() != null) {
            metadata.put(prefix + "credits_consumed", response.getCreditsConsumed());
        }

        ResponsibleAiEvalResponse.RailScore railScore = response.getRailScore();
        if (railScore != null) {
            if (railScore.getScore() != null) {
                metadata.put(prefix + "rail_score.score", railScore.getScore());
            }
            if (railScore.getConfidence() != null) {
                metadata.put(prefix + "rail_score.confidence", railScore.getConfidence());
            }
            if (railScore.getSummary() != null) {
                metadata.put(prefix + "rail_score.summary", railScore.getSummary());
            }
        }

        Map<String, ResponsibleAiEvalResponse.DimensionScore> dimensionScores = response.getDimensionScores();
        if (dimensionScores != null) {
            for (Map.Entry<String, ResponsibleAiEvalResponse.DimensionScore> entry : dimensionScores.entrySet()) {
                String dim = entry.getKey();
                ResponsibleAiEvalResponse.DimensionScore score = entry.getValue();
                if (score.getScore() != null) {
                    metadata.put(prefix + "dimension_scores." + dim + ".score", score.getScore());
                }
                if (score.getConfidence() != null) {
                    metadata.put(prefix + "dimension_scores." + dim + ".confidence", score.getConfidence());
                }
                if (score.getExplanation() != null) {
                    metadata.put(prefix + "dimension_scores." + dim + ".explanation", score.getExplanation());
                }
                if (score.getIssues() != null) {
                    metadata.put(prefix + "dimension_scores." + dim + ".issues", score.getIssues());
                }
                if (score.getSuggestions() != null) {
                    metadata.put(prefix + "dimension_scores." + dim + ".suggestions", score.getSuggestions());
                }
            }
        }
    }

    private ResponsibleAiEvalResponse executeRequest(ResponsibleAiEvalRequest requestPayload) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(baseUrl, "railscore/v1/eval")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j-ResponsibleAI")
                .body(ResponsibleAiJsonUtils.toJson(requestPayload))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        return ResponsibleAiJsonUtils.fromJson(rawResponse.body(), ResponsibleAiEvalResponse.class);
    }

    public ResponsibleAiToolCallResponse evaluateToolCall(String toolName, Map<String, Object> toolInput) {
        return evaluateToolCall(toolName, toolInput, null, null);
    }

    public ResponsibleAiToolCallResponse evaluateToolCall(
            String toolName, Map<String, Object> toolInput, String agentContext, List<String> allowedTools) {
        ResponsibleAiToolCallRequest requestPayload = ResponsibleAiToolCallRequest.builder()
                .toolName(toolName)
                .toolInput(toolInput)
                .agentContext(agentContext)
                .allowedTools(allowedTools)
                .build();
        return withRetryMappingExceptions(() -> executeToolCallRequest(requestPayload), maxRetries);
    }

    private ResponsibleAiToolCallResponse executeToolCallRequest(ResponsibleAiToolCallRequest requestPayload) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(baseUrl, "railscore/v1/agent/tool-call")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j-ResponsibleAI")
                .body(ResponsibleAiJsonUtils.toJson(requestPayload))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        return ResponsibleAiJsonUtils.fromJson(rawResponse.body(), ResponsibleAiToolCallResponse.class);
    }

    public ResponsibleAiToolResultResponse evaluateToolResult(String toolName, String toolResult) {
        return evaluateToolResult(toolName, toolResult, null, null);
    }

    public ResponsibleAiToolResultResponse evaluateToolResult(
            String toolName, String toolResult, String agentContext, Boolean redactPii) {
        ResponsibleAiToolResultRequest requestPayload = ResponsibleAiToolResultRequest.builder()
                .toolName(toolName)
                .toolResult(toolResult)
                .agentContext(agentContext)
                .redactPii(redactPii)
                .build();
        return withRetryMappingExceptions(() -> executeToolResultRequest(requestPayload), maxRetries);
    }

    private ResponsibleAiToolResultResponse executeToolResultRequest(ResponsibleAiToolResultRequest requestPayload) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(baseUrl, "railscore/v1/agent/tool-result")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j-ResponsibleAI")
                .body(ResponsibleAiJsonUtils.toJson(requestPayload))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        return ResponsibleAiJsonUtils.fromJson(rawResponse.body(), ResponsibleAiToolResultResponse.class);
    }

    public ResponsibleAiPromptInjectionResponse detectPromptInjection(String text) {
        return detectPromptInjection(text, null, null);
    }

    public ResponsibleAiPromptInjectionResponse detectPromptInjection(String text, String context, String sensitivity) {
        ResponsibleAiPromptInjectionRequest requestPayload = ResponsibleAiPromptInjectionRequest.builder()
                .text(text)
                .context(context)
                .sensitivity(sensitivity)
                .build();
        return withRetryMappingExceptions(() -> executePromptInjectionRequest(requestPayload), maxRetries);
    }

    private ResponsibleAiPromptInjectionResponse executePromptInjectionRequest(
            ResponsibleAiPromptInjectionRequest requestPayload) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(baseUrl, "railscore/v1/agent/prompt-injection")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j-ResponsibleAI")
                .body(ResponsibleAiJsonUtils.toJson(requestPayload))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        return ResponsibleAiJsonUtils.fromJson(rawResponse.body(), ResponsibleAiPromptInjectionResponse.class);
    }

    public static ResponsibleAiModerationModelBuilder builder() {
        for (ResponsibleAiModerationModelBuilderFactory factory :
                loadFactories(ResponsibleAiModerationModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ResponsibleAiModerationModelBuilder();
    }

    public static class ResponsibleAiModerationModelBuilder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String mode;
        private List<String> dimensions;
        private Map<String, Double> weights;
        private String domain;
        private Boolean includeExplanations;
        private Boolean includeIssues;
        private Boolean includeSuggestions;
        private Duration connectTimeout;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private List<ModerationModelListener> listeners;

        public ResponsibleAiModerationModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public ResponsibleAiModerationModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ResponsibleAiModerationModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ResponsibleAiModerationModelBuilder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public ResponsibleAiModerationModelBuilder dimensions(List<String> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public ResponsibleAiModerationModelBuilder weights(Map<String, Double> weights) {
            this.weights = weights;
            return this;
        }

        public ResponsibleAiModerationModelBuilder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public ResponsibleAiModerationModelBuilder includeExplanations(Boolean includeExplanations) {
            this.includeExplanations = includeExplanations;
            return this;
        }

        public ResponsibleAiModerationModelBuilder includeIssues(Boolean includeIssues) {
            this.includeIssues = includeIssues;
            return this;
        }

        public ResponsibleAiModerationModelBuilder includeSuggestions(Boolean includeSuggestions) {
            this.includeSuggestions = includeSuggestions;
            return this;
        }

        public ResponsibleAiModerationModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ResponsibleAiModerationModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ResponsibleAiModerationModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ResponsibleAiModerationModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ResponsibleAiModerationModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ResponsibleAiModerationModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public ResponsibleAiModerationModelBuilder listeners(List<ModerationModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public ResponsibleAiModerationModel build() {
            return new ResponsibleAiModerationModel(this);
        }
    }
}
