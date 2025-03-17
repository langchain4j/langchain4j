package dev.langchain4j.model.huggingface;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.model.huggingface.spi.HuggingFaceLanguageModelBuilderFactory;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;

public class HuggingFaceLanguageModel implements LanguageModel {

    private final HuggingFaceClient client;
    private final Double temperature;
    private final Integer maxNewTokens;
    private final Boolean returnFullText;
    private final Boolean waitForModel;

    public HuggingFaceLanguageModel(
            String accessToken,
            String modelId,
            Duration timeout,
            Double temperature,
            Integer maxNewTokens,
            Boolean returnFullText,
            Boolean waitForModel) {
        this(HuggingFaceLanguageModel.builder()
                .accessToken(accessToken)
                .modelId(modelId)
                .timeout(timeout)
                .temperature(temperature)
                .maxNewTokens(maxNewTokens)
                .returnFullText(returnFullText)
                .waitForModel(waitForModel));
    }

    public HuggingFaceLanguageModel(
            String baseUrl,
            String accessToken,
            String modelId,
            Duration timeout,
            Double temperature,
            Integer maxNewTokens,
            Boolean returnFullText,
            Boolean waitForModel) {
        this(HuggingFaceLanguageModel.builder()
                .baseUrl(baseUrl)
                .accessToken(accessToken)
                .modelId(modelId)
                .timeout(timeout)
                .temperature(temperature)
                .maxNewTokens(maxNewTokens)
                .returnFullText(returnFullText)
                .waitForModel(waitForModel));
    }

    public HuggingFaceLanguageModel(Builder builder) {
        this.client = FactoryCreator.FACTORY.create(new HuggingFaceClientFactory.Input() {
            @Override
            public String baseUrl() {
                return builder.baseUrl;
            }

            @Override
            public String apiKey() {
                return builder.accessToken;
            }

            @Override
            public String modelId() {
                return builder.modelId;
            }

            @Override
            public Duration timeout() {
                return builder.timeout;
            }
        });
        this.temperature = builder.temperature;
        this.maxNewTokens = builder.maxNewTokens;
        this.returnFullText = builder.returnFullText;
        this.waitForModel = builder.waitForModel;
    }

    @Override
    public Response<String> generate(String prompt) {

        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs(prompt)
                .parameters(Parameters.builder()
                        .temperature(temperature)
                        .maxNewTokens(maxNewTokens)
                        .returnFullText(returnFullText)
                        .build())
                .options(Options.builder().waitForModel(waitForModel).build())
                .build();

        TextGenerationResponse response = client.generate(request);

        return Response.from(response.getGeneratedText());
    }

    public static Builder builder() {
        for (HuggingFaceLanguageModelBuilderFactory factory :
                loadFactories(HuggingFaceLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static final class Builder {

        private String baseUrl;
        private String accessToken;
        private String modelId;
        private Duration timeout = Duration.ofSeconds(15);
        private Double temperature;
        private Integer maxNewTokens;
        private Boolean returnFullText = false;
        private Boolean waitForModel = true;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder modelId(String modelId) {
            if (modelId != null) {
                this.modelId = modelId;
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            if (timeout != null) {
                this.timeout = timeout;
            }
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxNewTokens(Integer maxNewTokens) {
            this.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder returnFullText(Boolean returnFullText) {
            if (returnFullText != null) {
                this.returnFullText = returnFullText;
            }
            return this;
        }

        public Builder waitForModel(Boolean waitForModel) {
            if (waitForModel != null) {
                this.waitForModel = waitForModel;
            }
            return this;
        }

        public HuggingFaceLanguageModel build() {
            if (accessToken == null || accessToken.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
            }
            return new HuggingFaceLanguageModel(this);
        }
    }

    public static HuggingFaceLanguageModel withAccessToken(String accessToken) {
        return builder().accessToken(accessToken).build();
    }
}
