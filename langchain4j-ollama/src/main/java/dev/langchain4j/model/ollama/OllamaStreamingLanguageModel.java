package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toOllamaResponseFormat;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.ollama.spi.OllamaStreamingLanguageModelBuilderFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaStreamingLanguageModel implements StreamingLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final ResponseFormat responseFormat;

    public OllamaStreamingLanguageModel(OllamaStreamingLanguageModelBuilder builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .customHeaders(builder.customHeaders)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.options = Options.builder()
                .temperature(builder.temperature)
                .topK(builder.topK)
                .topP(builder.topP)
                .repeatPenalty(builder.repeatPenalty)
                .seed(builder.seed)
                .numPredict(builder.numPredict)
                .numCtx(builder.numCtx)
                .stop(builder.stop)
                .build();
        this.responseFormat = builder.responseFormat;
    }

    public static OllamaStreamingLanguageModelBuilder builder() {
        for (OllamaStreamingLanguageModelBuilderFactory factory :
                loadFactories(OllamaStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaStreamingLanguageModelBuilder();
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .options(options)
                .format(toOllamaResponseFormat(responseFormat))
                .stream(true)
                .build();

        client.streamingCompletion(request, handler);
    }

    public static class OllamaStreamingLanguageModelBuilder {

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
        private ResponseFormat responseFormat;
        private Duration timeout;
        private Map<String, String> customHeaders;
        private Boolean logRequests;
        private Boolean logResponses;

        public OllamaStreamingLanguageModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets the {@link HttpClientBuilder} that will be used to create the {@link HttpClient}
         * that will be used to communicate with Ollama.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
         */
        public OllamaStreamingLanguageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaStreamingLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaStreamingLanguageModel build() {
            return new OllamaStreamingLanguageModel(this);
        }
    }
}
