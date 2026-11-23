package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;
import static dev.langchain4j.model.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;
import static java.util.stream.Collectors.toList;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.credential.Credential;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OpenAiOfficialEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final OpenAIClient client;
    private final String modelName;
    private final Integer dimensions;
    private final String user;
    private final String encodingFormat;
    private final Map<String, Object> customParameters;
    private final Integer maxSegmentsPerBatch;

    public OpenAiOfficialEmbeddingModel(Builder builder) {

        if (builder.openAIClient != null) {
            this.client = builder.openAIClient;
        } else {
            this.client = setupSyncClient(
                    builder.baseUrl,
                    builder.apiKey,
                    builder.credential,
                    builder.microsoftFoundryDeploymentName,
                    builder.azureOpenAIServiceVersion,
                    builder.organizationId,
                    builder.isMicrosoftFoundry,
                    builder.isGitHubModels,
                    builder.modelName,
                    builder.timeout,
                    builder.maxRetries,
                    builder.proxy,
                    builder.customHeaders);
        }
        this.modelName = builder.modelName;
        this.dimensions = getOrDefault(builder.dimensions, knownDimension());
        this.user = builder.user;
        this.encodingFormat = builder.encodingFormat;
        this.customParameters = copyIfNotNull(builder.customParameters);
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, 2048);
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        List<List<String>> textBatches = partition(texts, maxSegmentsPerBatch);

        return embedBatchedTexts(textBatches);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(
                EmbeddingRequestParameters.MODEL_NAME,
                EmbeddingRequestParameters.DIMENSIONS,
                OpenAiOfficialEmbeddingRequestParameters.USER,
                OpenAiOfficialEmbeddingRequestParameters.ENCODING_FORMAT,
                OpenAiOfficialEmbeddingRequestParameters.CUSTOM_PARAMETERS);
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters() {
        return OpenAiOfficialEmbeddingRequestParameters.builder()
                .modelName(modelName)
                .dimensions(dimensions)
                .user(user)
                .encodingFormat(encodingFormat)
                .customParameters(customParameters)
                .build();
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {

        EmbeddingRequestParameters parameters = request.parameters();

        List<String> texts = request.inputs().stream().map(EmbeddingInput::text).toList();

        List<EmbeddedBatch> batches = new ArrayList<>();
        for (List<String> batch : partition(texts, maxSegmentsPerBatch)) {
            batches.add(embedTexts(batch, parameters));
        }

        List<Embedding> embeddings =
                batches.stream().flatMap(batch -> batch.embeddings().stream()).toList();
        TokenUsage tokenUsage = batches.stream()
                .map(EmbeddedBatch::tokenUsage)
                .filter(Objects::nonNull)
                .reduce(TokenUsage::add)
                .orElse(null);
        String responseModelName = batches.stream()
                .map(EmbeddedBatch::modelName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(getOrDefault(responseModelName, getOrDefault(parameters.modelName(), modelName)))
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    private record EmbeddedBatch(List<Embedding> embeddings, TokenUsage tokenUsage, String modelName) {}

    private List<List<String>> partition(List<String> inputList, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += size) {
            int toIndex = Math.min(i + size, inputList.size());
            result.add(inputList.subList(i, toIndex));
        }
        return result;
    }

    private Response<List<Embedding>> embedBatchedTexts(List<List<String>> textBatches) {
        List<EmbeddedBatch> responses = new ArrayList<>();
        for (List<String> batch : textBatches) {
            responses.add(embedTexts(batch, defaultRequestParameters()));
        }
        return Response.from(
                responses.stream()
                        .flatMap(response -> response.embeddings().stream())
                        .toList(),
                responses.stream()
                        .map(EmbeddedBatch::tokenUsage)
                        .filter(Objects::nonNull)
                        .reduce(TokenUsage::add)
                        .orElse(null));
    }

    @SuppressWarnings("unchecked")
    private EmbeddedBatch embedTexts(List<String> texts, EmbeddingRequestParameters parameters) {

        EmbeddingCreateParams.Input input = EmbeddingCreateParams.Input.ofArrayOfStrings(texts);

        EmbeddingCreateParams.Builder embeddingCreateParamsBuilder = EmbeddingCreateParams.builder();
        embeddingCreateParamsBuilder.input(input);
        embeddingCreateParamsBuilder.model(getOrDefault(parameters.modelName(), modelName));
        String requestUser = parameters.parameter(OpenAiOfficialEmbeddingRequestParameters.USER);
        if (requestUser != null) {
            embeddingCreateParamsBuilder.user(requestUser);
        }
        Integer requestDimensions = getOrDefault(parameters.dimensions(), dimensions);
        if (requestDimensions != null) {
            embeddingCreateParamsBuilder.dimensions(requestDimensions);
        }
        String encodingFormat = parameters.parameter(OpenAiOfficialEmbeddingRequestParameters.ENCODING_FORMAT);
        if (encodingFormat != null) {
            embeddingCreateParamsBuilder.encodingFormat(EmbeddingCreateParams.EncodingFormat.of(encodingFormat));
        }
        Map<String, Object> customParameters =
                parameters.parameter(OpenAiOfficialEmbeddingRequestParameters.CUSTOM_PARAMETERS);
        if (customParameters != null) {
            customParameters.forEach((name, value) ->
                    embeddingCreateParamsBuilder.putAdditionalBodyProperty(name, JsonValue.from(value)));
        }

        final CreateEmbeddingResponse createEmbeddingResponse =
                client.embeddings().create(embeddingCreateParamsBuilder.build());

        List<Embedding> embeddings = createEmbeddingResponse.data().stream()
                .map(embeddingItem -> Embedding.from(embeddingItem.embedding()))
                .toList();

        return new EmbeddedBatch(
                embeddings, tokenUsageFrom(createEmbeddingResponse.usage()), createEmbeddingResponse.model());
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
        private String microsoftFoundryDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isMicrosoftFoundry;
        private boolean isGitHubModels;
        private OpenAIClient openAIClient;
        private String modelName;
        private Integer dimensions;
        private String user;
        private String encodingFormat;
        private Map<String, Object> customParameters;
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

        /**
         * @deprecated Use {@link #microsoftFoundryDeploymentName(String)} instead
         */
        @Deprecated
        public Builder azureDeploymentName(String azureDeploymentName) {
            this.microsoftFoundryDeploymentName = azureDeploymentName;
            return this;
        }

        public Builder microsoftFoundryDeploymentName(String microsoftFoundryDeploymentName) {
            this.microsoftFoundryDeploymentName = microsoftFoundryDeploymentName;
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

        /**
         * @deprecated Use {@link #isMicrosoftFoundry(boolean)} instead
         */
        @Deprecated
        public Builder isAzure(boolean isAzure) {
            this.isMicrosoftFoundry = isAzure;
            return this;
        }

        public Builder isMicrosoftFoundry(boolean isMicrosoftFoundry) {
            this.isMicrosoftFoundry = isMicrosoftFoundry;
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

        public Builder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
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
