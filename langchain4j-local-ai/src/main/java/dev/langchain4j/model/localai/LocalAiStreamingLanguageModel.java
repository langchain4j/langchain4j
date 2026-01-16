package dev.langchain4j.model.localai;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.localai.spi.LocalAiStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.openai.OpenAiStreamingResponseBuilder;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * See <a href="https://localai.io/features/text-generation/">LocalAI documentation</a> for more details.
 */
public class LocalAiStreamingLanguageModel implements StreamingLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;

    @Deprecated(forRemoval = true, since = "1.5.0")
    public LocalAiStreamingLanguageModel(String baseUrl,
                                         String modelName,
                                         Double temperature,
                                         Double topP,
                                         Integer maxTokens,
                                         Duration timeout,
                                         Boolean logRequests,
                                         Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;

        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    public LocalAiStreamingLanguageModel(LocalAiStreamingLanguageModelBuilder builder) {
        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(builder.baseUrl, "baseUrl"))
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.temperature = getOrDefault(builder.temperature, 0.7);
        this.topP = builder.topP;
        this.maxTokens = builder.maxTokens;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .build();

        OpenAiStreamingResponseBuilder responseBuilder = new OpenAiStreamingResponseBuilder();

        client.completion(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    String token = partialResponse.text();
                    if (token != null) {
                        handler.onNext(token);
                    }
                })
                .onComplete(() -> {
                    ChatResponse chatResponse = responseBuilder.build();
                    handler.onComplete(Response.from(
                            chatResponse.aiMessage().text(),
                            chatResponse.metadata().tokenUsage(),
                            chatResponse.metadata().finishReason()
                    ));
                })
                .onError(handler::onError)
                .execute();
    }

    public static LocalAiStreamingLanguageModelBuilder builder() {
        for (LocalAiStreamingLanguageModelBuilderFactory factory : loadFactories(LocalAiStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new LocalAiStreamingLanguageModelBuilder();
    }

    public static class LocalAiStreamingLanguageModelBuilder {
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public LocalAiStreamingLanguageModelBuilder() {
            // This is public so it can be extended
        }

        public LocalAiStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public LocalAiStreamingLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public LocalAiStreamingLanguageModel build() {
            return new LocalAiStreamingLanguageModel(this);
        }

        public String toString() {
            return "LocalAiStreamingLanguageModel.LocalAiStreamingLanguageModelBuilder(baseUrl=" + this.baseUrl + ", modelName=" + this.modelName + ", temperature=" + this.temperature + ", topP=" + this.topP + ", maxTokens=" + this.maxTokens + ", timeout=" + this.timeout + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
