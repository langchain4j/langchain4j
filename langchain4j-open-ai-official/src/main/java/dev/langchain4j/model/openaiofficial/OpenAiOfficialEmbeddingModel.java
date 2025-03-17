package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.detectModelHost;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.setupSyncClient;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;
import static java.util.stream.Collectors.toList;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.credential.Credential;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OpenAiOfficialEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final OpenAIClient client;
    private InternalOpenAiOfficialHelper.ModelHost modelHost;
    private final String modelName;
    private final Integer dimensions;
    private final String user;
    private final Integer maxSegmentsPerBatch;

    public OpenAiOfficialEmbeddingModel(Builder builder) {

        this.modelHost = detectModelHost(
                builder.isAzure,
                builder.isGitHubModels,
                builder.baseUrl,
                builder.azureDeploymentName,
                builder.azureOpenAIServiceVersion);

        this.client = setupSyncClient(
                builder.baseUrl,
                builder.apiKey,
                builder.credential,
                builder.azureDeploymentName,
                builder.azureOpenAIServiceVersion,
                builder.organizationId,
                this.modelHost,
                builder.openAIClient,
                builder.modelName,
                builder.timeout,
                builder.maxRetries,
                builder.proxy,
                builder.customHeaders);
        this.modelName = builder.modelName;
        this.dimensions = getOrDefault(builder.dimensions, knownDimension());
        this.user = builder.user;
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, 2048);
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        List<List<String>> textBatches = partition(texts, maxSegmentsPerBatch);

        return embedBatchedTexts(textBatches);
    }

    private List<List<String>> partition(List<String> inputList, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += size) {
            int toIndex = Math.min(i + size, inputList.size());
            result.add(inputList.subList(i, toIndex));
        }
        return result;
    }

    private Response<List<Embedding>> embedBatchedTexts(List<List<String>> textBatches) {
        List<Response<List<Embedding>>> responses = new ArrayList<>();
        for (List<String> batch : textBatches) {
            Response<List<Embedding>> response = embedTexts(batch);
            responses.add(response);
        }
        return Response.from(
                responses.stream()
                        .flatMap(response -> response.content().stream())
                        .toList(),
                responses.stream()
                        .map(Response::tokenUsage)
                        .filter(Objects::nonNull)
                        .reduce(TokenUsage::add)
                        .orElse(null));
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingCreateParams.Input input = EmbeddingCreateParams.Input.ofArrayOfStrings(texts);

        EmbeddingCreateParams.Builder embeddingCreateParamsBuilder = EmbeddingCreateParams.builder();
        embeddingCreateParamsBuilder.input(input);
        embeddingCreateParamsBuilder.model(modelName);
        if (user != null) {
            embeddingCreateParamsBuilder.user(user);
        }
        if (dimensions != null) {
            embeddingCreateParamsBuilder.dimensions(dimensions);
        }

        final CreateEmbeddingResponse createEmbeddingResponse =
                client.embeddings().create(embeddingCreateParamsBuilder.build());

        List<Embedding> embeddings = createEmbeddingResponse.data().stream()
                .map(embeddingItem -> Embedding.from(embeddingItem.embedding().stream()
                        .map(Double::floatValue)
                        .toList()))
                .toList();

        return Response.from(embeddings, tokenUsageFrom(createEmbeddingResponse.usage()));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Integer knownDimension() {
        if (dimensions != null) {
            return dimensions;
        } else {
            return OpenAiOfficialEmbeddingModelName.knownDimension(modelName);
        }
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Credential credential;
        private String azureDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isAzure;
        private boolean isGitHubModels;
        private OpenAIClient openAIClient;
        private String modelName;
        private Integer dimensions;
        private String user;
        private Integer maxSegmentsPerBatch;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Map<String, String> customHeaders;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public Builder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder isAzure(boolean isAzure) {
            this.isAzure = isAzure;
            return this;
        }

        public Builder isGitHubModels(boolean isGitHubModels) {
            this.isGitHubModels = isGitHubModels;
            return this;
        }

        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(EmbeddingModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

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

        public OpenAiOfficialEmbeddingModel build() {
            return new OpenAiOfficialEmbeddingModel(this);
        }
    }
}
