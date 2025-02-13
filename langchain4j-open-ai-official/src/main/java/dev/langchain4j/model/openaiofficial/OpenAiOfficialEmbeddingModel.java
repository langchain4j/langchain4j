package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.credential.Credential;
import com.openai.models.CreateEmbeddingResponse;
import com.openai.models.EmbeddingCreateParams;
import com.openai.models.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.openaiofficial.spi.OpenAiOfficialEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.setupSyncClient;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

public class OpenAiOfficialEmbeddingModel extends DimensionAwareEmbeddingModel implements TokenCountEstimator {

    private final OpenAIClient client;
    private final boolean useAzure;
    private final String modelName;
    private final Tokenizer tokenizer;
    private final Integer dimensions;
    private final String user;
    private final Integer maxSegmentsPerBatch;

    public OpenAiOfficialEmbeddingModel(String baseUrl,
                                        String apiKey,
                                        String azureApiKey,
                                        Credential credential,
                                        String azureDeploymentName,
                                        AzureOpenAIServiceVersion azureOpenAIServiceVersion,
                                        String organizationId,
                                        String modelName,
                                        Integer dimensions,
                                        String user,
                                        Integer maxSegmentsPerBatch,
                                        Duration timeout,
                                        Integer maxRetries,
                                        Proxy proxy,
                                        Tokenizer tokenizer,
                                        Map<String, String> customHeaders) {

        if (azureApiKey != null || credential != null) {
            // Using Azure OpenAI
            this.useAzure = true;
            ensureNotBlank(modelName, "modelName");
        } else {
            // Using OpenAI
            this.useAzure = false;
        }

        this.client = setupSyncClient(baseUrl, useAzure, apiKey, azureApiKey, credential, azureDeploymentName, azureOpenAIServiceVersion, organizationId, modelName, timeout, maxRetries, proxy, customHeaders);
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.tokenizer = tokenizer;
        this.user = user;
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, 2048);
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

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
        Integer dimensions = knownDimension();
        if (dimensions != null) {
            embeddingCreateParamsBuilder.dimensions(dimensions);
        }


        final CreateEmbeddingResponse createEmbeddingResponse = client.embeddings().create(embeddingCreateParamsBuilder.build());

        List<Embedding> embeddings = createEmbeddingResponse.data().stream()
                .map(embeddingItem -> Embedding.from(embeddingItem
                        .embedding()
                        .stream()
                        .map(Double::floatValue)
                        .toList()))
                .toList();

        return Response.from(embeddings, tokenUsageFrom(createEmbeddingResponse.usage()));
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    public static OpenAiOfficialEmbeddingModelBuilder builder() {
        for (OpenAiOfficialEmbeddingModelBuilderFactory factory : loadFactories(OpenAiOfficialEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiOfficialEmbeddingModelBuilder();
    }

    @Override
    protected Integer knownDimension() {
        if (dimensions != null) {
            return dimensions;
        } else {
            return OpenAiOfficialEmbeddingModelName.knownDimension(modelName);
        }
    }

    public static class OpenAiOfficialEmbeddingModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String azureApiKey;
        private Credential credential;
        private String azureDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private String modelName;
        private Integer dimensions;
        private String user;
        private Integer maxSegmentsPerBatch;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;

        public OpenAiOfficialEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder azureApiKey(String azureApiKey) {
            this.azureApiKey = azureApiKey;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder modelName(EmbeddingModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public OpenAiOfficialEmbeddingModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiOfficialEmbeddingModel build() {

            return new OpenAiOfficialEmbeddingModel(
                    baseUrl,
                    apiKey,
                    azureApiKey,
                    credential,
                    azureDeploymentName,
                    azureOpenAIServiceVersion,
                    organizationId,
                    modelName,
                    dimensions,
                    user,
                    maxSegmentsPerBatch,
                    timeout,
                    maxRetries,
                    proxy,
                    tokenizer,
                    customHeaders);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OpenAiOfficialEmbeddingModelBuilder.class.getSimpleName() + "[", "]")
                    .add("baseUrl='" + baseUrl + "'")
                    .add("azureDeploymentName=" + azureDeploymentName)
                    .add("azureOpenAIServiceVersion='" + azureOpenAIServiceVersion + "'")
                    .add("organizationId='" + organizationId + "'")
                    .add("modelName='" + modelName + "'")
                    .add("dimensions=" + dimensions)
                    .add("user=" + user)
                    .add("maxSegmentsPerBatch=" + maxSegmentsPerBatch)
                    .add("timeout=" + timeout)
                    .add("maxRetries=" + maxRetries)
                    .add("proxy=" + proxy)
                    .add("tokenizer=" + tokenizer)
                    .add("customHeaders=" + customHeaders)
                    .toString();
        }
    }
}
