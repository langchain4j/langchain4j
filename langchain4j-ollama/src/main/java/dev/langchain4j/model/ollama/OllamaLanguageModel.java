package dev.langchain4j.model.ollama;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.spi.OllamaLanguageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaResponseFormat;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaLanguageModel implements LanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final ResponseFormat responseFormat;
    private final Integer maxRetries;

    public OllamaLanguageModel(HttpClientBuilder httpClientBuilder,
                               String baseUrl,
                               String modelName,
                               Double temperature,
                               Integer topK,
                               Double topP,
                               Double repeatPenalty,
                               Integer seed,
                               Integer numPredict,
                               Integer numCtx,
                               List<String> stop,
                               String format,
                               ResponseFormat responseFormat,
                               Duration timeout,
                               Integer maxRetries,
                               Boolean logRequests,
                               Boolean logResponses,
                               Map<String, String> customHeaders
    ) {
        if (format != null && responseFormat != null) {
            throw new IllegalStateException("Cant use both 'format' and 'responseFormat' parameters");
        }

        this.client = OllamaClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customHeaders(customHeaders)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.options = Options.builder()
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .repeatPenalty(repeatPenalty)
                .seed(seed)
                .numPredict(numPredict)
                .numCtx(numCtx)
                .stop(stop)
                .build();
        this.responseFormat = "json".equals(format) ? ResponseFormat.JSON : responseFormat;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public static OllamaLanguageModelBuilder builder() {
        for (OllamaLanguageModelBuilderFactory factory : loadFactories(OllamaLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaLanguageModelBuilder();
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .options(options)
                .format(toOllamaResponseFormat(responseFormat))
                .stream(false)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request), maxRetries);

        return Response.from(
                response.getResponse(),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }

    public static class OllamaLanguageModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repeatPenalty;
        private Integer seed;
        private Integer numPredict;
        private Integer numCtx;
        private List<String> stop;
        private String format;
        private ResponseFormat responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public OllamaLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        /**
         * TODO
         * TODO {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}
         *
         * @param httpClientBuilder
         * @return
         */
        public OllamaLanguageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OllamaLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OllamaLanguageModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public OllamaLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OllamaLanguageModelBuilder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public OllamaLanguageModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OllamaLanguageModelBuilder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        public OllamaLanguageModelBuilder numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return this;
        }

        public OllamaLanguageModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        /**
         * @deprecated Please use {@link #responseFormat(ResponseFormat)} instead.
         * For example: {@code responseFormat(ResponseFormat.JSON)}.
         * <br>
         * Instead of using JSON mode, consider using structured outputs with JSON schema instead,
         * see more info <a href="https://docs.langchain4j.dev/tutorials/structured-outputs#json-schema">here</a>.
         */
        @Deprecated
        public OllamaLanguageModelBuilder format(String format) {
            this.format = format;
            return this;
        }

        public OllamaLanguageModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OllamaLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaLanguageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OllamaLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OllamaLanguageModel build() {
            return new OllamaLanguageModel(
                    httpClientBuilder,
                    baseUrl,
                    modelName,
                    temperature,
                    topK,
                    topP,
                    repeatPenalty,
                    seed,
                    numPredict,
                    numCtx,
                    stop,
                    format,
                    responseFormat,
                    timeout,
                    maxRetries,
                    logRequests,
                    logResponses,
                    customHeaders
            );
        }
    }
}
