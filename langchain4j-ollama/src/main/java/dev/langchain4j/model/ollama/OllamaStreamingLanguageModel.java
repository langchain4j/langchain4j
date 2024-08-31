package dev.langchain4j.model.ollama;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.ollama.spi.OllamaStreamingLanguageModelBuilderFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaStreamingLanguageModel implements StreamingLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;

    public OllamaStreamingLanguageModel(String baseUrl,
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
                                        Duration timeout,
                                        Boolean logRequests,
                                        Boolean logResponses,
                                        Map<String, String> customHeaders
    ) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
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
        this.format = format;
    }

    public static OllamaStreamingLanguageModelBuilder builder() {
        for (OllamaStreamingLanguageModelBuilderFactory factory : loadFactories(OllamaStreamingLanguageModelBuilderFactory.class)) {
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
                .format(format)
                .stream(true)
                .build();

        client.streamingCompletion(request, handler);
    }

    public static class OllamaStreamingLanguageModelBuilder {

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
        private Duration timeout;
        private Map<String, String> customHeaders;
        private Boolean logRequests;
        private Boolean logResponses;

        public OllamaStreamingLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
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

        public OllamaStreamingLanguageModelBuilder format(String format) {
            this.format = format;
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
            return new OllamaStreamingLanguageModel(
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
                    timeout,
                    logRequests,
                    logResponses,
                    customHeaders
            );
        }
    }
}
