package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialDecisionHelper.toDecisionCreateParams;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialDecisionHelper.toDecisionResponse;
import static dev.langchain4j.model.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;

import com.openai.client.OpenAIClient;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link DecisionModel} backed by the <a href="https://developers.openai.com/api/docs/guides/decisions">OpenAI
 * Decisions API</a>, using the official OpenAI Java SDK. It answers yes/no, choice and scale questions about a text
 * or image input with probabilities.
 * <pre>{@code
 * DecisionModel model = OpenAiOfficialDecisionModel.builder()
 *         .apiKey(System.getenv("OPENAI_API_KEY"))
 *         .modelName("gpt-6-luna")
 *         .build();
 * }</pre>
 * The input can be text, a {@link java.util.Map} (sent as JSON text), or a list of
 * {@link dev.langchain4j.data.message.TextContent}s and {@link dev.langchain4j.data.message.ImageContent}s. Images
 * must be inline (base64 data or data URLs): images referenced by URL are not supported by the API. The image detail
 * levels {@code LOW}, {@code HIGH} and {@code AUTO} are supported.
 * <p>
 * The API can refuse to answer a single question, for example because it goes against the usage policies of
 * OpenAI: the answer to that question is then a {@link dev.langchain4j.model.decision.response.RefusalAnswer}.
 *
 * @since 1.22.0
 */
@Experimental
public class OpenAiOfficialDecisionModel implements DecisionModel {

    private final OpenAIClient client;
    private final DecisionRequestParameters defaultRequestParameters;
    private final List<DecisionModelListener> listeners;

    public OpenAiOfficialDecisionModel(Builder builder) {
        if (builder.openAIClient != null) {
            this.client = builder.openAIClient;
        } else {
            this.client = setupSyncClient(
                    builder.baseUrl,
                    builder.apiKey,
                    null,
                    null,
                    null,
                    builder.organizationId,
                    false,
                    false,
                    builder.modelName,
                    builder.timeout,
                    builder.maxRetries,
                    builder.proxy,
                    builder.customHeaders);
        }
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(builder.modelName).build();
        this.listeners = copy(builder.listeners);
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        DecisionCreateParams params = toDecisionCreateParams(request);
        // the OpenAI SDK retries transient errors itself, so withRetry is not used here
        Decision decision;
        try {
            decision = client.decisions().create(params);
        } catch (Exception e) {
            throw OpenAiOfficialExceptionMapper.INSTANCE.mapException(e);
        }
        return toDecisionResponse(decision, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        DecisionCreateParams params = toDecisionCreateParams(request);
        CompletableFuture<Decision> decision = client.async().decisions().create(params);
        CompletableFuture<DecisionResponse> result = decision.handle((response, error) -> {
            if (error != null) {
                throw OpenAiOfficialExceptionMapper.INSTANCE.mapException(unwrapCompletionException(error));
            }
            return toDecisionResponse(response, request);
        });
        propagateCancellation(result, decision);
        return result;
    }

    @Override
    public DecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private OpenAIClient openAIClient;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Map<String, String> customHeaders;
        private List<DecisionModelListener> listeners;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Sets a preconfigured OpenAI client. The other client settings (base URL, API key, timeout...) are then
         * ignored.
         */
        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        /**
         * The model to use when a request does not specify one, for example {@code gpt-6-luna}. The model name must be
         * set either here or on each request.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The timeout of a call. Default value is 60 seconds.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The number of retries of the OpenAI SDK for transient errors such as timeouts, rate limits and server
         * errors. Default value is 3.
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder listeners(List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public OpenAiOfficialDecisionModel build() {
            return new OpenAiOfficialDecisionModel(this);
        }
    }
}
