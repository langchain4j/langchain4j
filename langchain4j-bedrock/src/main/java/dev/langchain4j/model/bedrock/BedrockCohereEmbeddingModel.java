package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.bedrock.internal.Json.fromJson;
import static dev.langchain4j.model.bedrock.internal.Json.toJson;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static software.amazon.awssdk.regions.Region.US_EAST_1;

/**
 * Bedrock Cohere embedding model with support for both versions:
 * {@code cohere.embed-english-v3} and {@code cohere.embed-multilingual-v3}
 * <br>
 * See more details <a href="https://docs.cohere.com/v2/docs/amazon-bedrock">here</a> and
 * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html">here</a>.
 */
public class BedrockCohereEmbeddingModel implements EmbeddingModel {

    private final BedrockRuntimeClient client;
    private final String model;
    private final String inputType;
    private final String truncate;
    private final int maxRetries;

    public BedrockCohereEmbeddingModel(Builder builder) {
        this.client = getOrDefault(builder.client, () -> initClient(builder));
        this.model = ensureNotBlank(builder.model, "model");
        this.inputType = ensureNotBlank(builder.inputType, "inputType");
        this.truncate = builder.truncate;
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    private BedrockRuntimeClient initClient(Builder builder) {
        return BedrockRuntimeClient.builder()
                .region(getOrDefault(builder.region, US_EAST_1))
                .credentialsProvider(getOrDefault(builder.credentialsProvider,
                        () -> DefaultCredentialsProvider.builder().build()))
                .build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        Map<String, Object> requestParameters = toRequestParameters(textSegments);
        String requestJson = toJson(requestParameters);

        InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(requestJson), maxRetries);

        String responseJson = invokeModelResponse.body().asUtf8String();
        BedrockCohereEmbeddingResponse embeddingResponse = fromJson(responseJson, BedrockCohereEmbeddingResponse.class);

        List<Embedding> embeddings = stream(embeddingResponse.getEmbeddings().getFloatEmbeddings())
                .map(Embedding::from)
                .collect(toList());

        return Response.from(embeddings);
    }

    private Map<String, Object> toRequestParameters(List<TextSegment> textSegments) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("texts", textSegments.stream().map(TextSegment::text).collect(toList()));
        parameters.put("input_type", inputType);
        parameters.put("truncate", truncate);
        parameters.put("embedding_types", List.of("float"));
        return parameters;
    }

    private InvokeModelResponse invoke(String body) {
        InvokeModelRequest invokeModelRequest = InvokeModelRequest.builder()
                .modelId(model)
                .body(SdkBytes.fromString(body, Charset.defaultCharset()))
                .build();
        return client.invokeModel(invokeModelRequest);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String model;
        private String inputType;
        private String truncate;
        private BedrockRuntimeClient client;
        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private Integer maxRetries;

        public Builder model(Model model) {
            return model(model.getValue());
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder inputType(InputType inputType) {
            return inputType(inputType.getValue());
        }

        public Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public Builder truncate(Truncate truncate) {
            return truncate(truncate.getValue());
        }

        public Builder truncate(String truncate) {
            this.truncate = truncate;
            return this;
        }

        public Builder client(BedrockRuntimeClient client) {
            this.client = client;
            return this;
        }

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public BedrockCohereEmbeddingModel build() {
            return new BedrockCohereEmbeddingModel(this);
        }
    }

    public enum Model {

        COHERE_EMBED_ENGLISH_V3("cohere.embed-english-v3"),
        COHERE_EMBED_MULTILINGUAL_V3("cohere.embed-multilingual-v3");

        private final String value;

        Model(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum InputType {

        SEARCH_DOCUMENT("search_document"),
        SEARCH_QUERY("search_query"),
        CLASSIFICATION("classification"),
        CLUSTERING("clustering");

        private final String value;

        InputType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum Truncate {

        NONE("NONE"),
        START("START"),
        END("END");

        private final String value;

        Truncate(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
