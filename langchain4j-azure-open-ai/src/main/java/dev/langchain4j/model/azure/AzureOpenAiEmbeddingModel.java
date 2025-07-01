package dev.langchain4j.model.azure;

import static dev.langchain4j.data.embedding.Embedding.from;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupSyncClient;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.azure.spi.AzureOpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents an OpenAI embedding model, hosted on Azure, such as text-embedding-ada-002.
 * <p>
 * Mandatory parameters for initialization are: endpoint and apikey (or an alternate authentication method, see below for more information).
 * Optionally you can set serviceVersion (if not, the latest version is used) and deploymentName (if not, a default name is used).
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
 * <p>
 * There are 3 authentication methods:
 * <p>
 * 1. Azure OpenAI API Key Authentication: this is the most common method, using an Azure OpenAI API key.
 * You need to provide the OpenAI API Key as a parameter, using the apiKey() method in the Builder, or the apiKey parameter in the constructor:
 * For example, you would use `builder.apiKey("{key}")`.
 * <p>
 * 2. non-Azure OpenAI API Key Authentication: this method allows to use the OpenAI service, instead of Azure OpenAI.
 * You can use the nonAzureApiKey() method in the Builder, which will also automatically set the endpoint to "https://api.openai.com/v1".
 * For example, you would use `builder.nonAzureApiKey("{key}")`.
 * The constructor requires a KeyCredential instance, which can be created using `new AzureKeyCredential("{key}")`, and doesn't set up the endpoint.
 * <p>
 * 3. Azure OpenAI client with Microsoft Entra ID (formerly Azure Active Directory) credentials.
 * - This requires to add the `com.azure:azure-identity` dependency to your project.
 * - You need to provide a TokenCredential instance, using the tokenCredential() method in the Builder, or the tokenCredential parameter in the constructor.
 * As an example, DefaultAzureCredential can be used to authenticate the client: Set the values of the client ID, tenant ID, and
 * client secret of the AAD application as environment variables: AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_CLIENT_SECRET.
 * Then, provide the DefaultAzureCredential instance to the builder: `builder.tokenCredential(new DefaultAzureCredentialBuilder().build())`.
 */
public class AzureOpenAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final int BATCH_SIZE = 16;

    private final OpenAIClient client;
    private final String deploymentName;
    private final Integer dimensions;

    public AzureOpenAiEmbeddingModel(Builder builder) {
        if (builder.openAIClient == null) {
            if (builder.tokenCredential != null) {
                this.client = setupSyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.tokenCredential,
                        builder.timeout,
                        builder.maxRetries,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            } else if (builder.keyCredential != null) {
                this.client = setupSyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.keyCredential,
                        builder.timeout,
                        builder.maxRetries,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            } else {
                this.client = setupSyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.apiKey,
                        builder.timeout,
                        builder.maxRetries,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            }
        } else {
            this.client = ensureNotNull(builder.openAIClient, "openAIClient");
        }

        this.deploymentName = ensureNotBlank(builder.deploymentName, "deploymentName");
        this.dimensions = builder.dimensions;
    }

    /**
     * Embeds the provided text segments, processing a maximum of 16 segments at a time.
     * For more information, refer to the documentation <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/faq#i-am-trying-to-use-embeddings-and-received-the-error--invalidrequesterror--too-many-inputs--the-max-number-of-inputs-is-1---how-do-i-fix-this-">here</a>.
     *
     * @param textSegments A list of text segments.
     * @return A list of corresponding embeddings.
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        List<Embedding> embeddings = new ArrayList<>();

        int inputTokenCount = 0;
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {

            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            EmbeddingsOptions options = new EmbeddingsOptions(batch).setDimensions(dimensions);

            Embeddings response = AzureOpenAiExceptionMapper.INSTANCE.withExceptionMapper(() ->
                    client.getEmbeddings(deploymentName, options));

            for (EmbeddingItem embeddingItem : response.getData()) {
                Embedding embedding = from(embeddingItem.getEmbedding());
                embeddings.add(embedding);
            }

            inputTokenCount += response.getUsage().getPromptTokens();
        }

        return Response.from(embeddings, new TokenUsage(inputTokenCount));
    }

    public static Builder builder() {
        for (AzureOpenAiEmbeddingModelBuilderFactory factory :
                loadFactories(AzureOpenAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    @Override
    protected Integer knownDimension() {
        if (dimensions != null) {
            return dimensions;
        } else {
            return AzureOpenAiEmbeddingModelName.knownDimension(deploymentName);
        }
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private HttpClientProvider httpClientProvider;
        private String deploymentName;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;
        private String userAgentSuffix;
        private Integer dimensions;
        private Map<String, String> customHeaders;

        /**
         * Sets the Azure OpenAI endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI endpoint in the format: https://{resource}.openai.azure.com/
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API service version. This is a mandatory parameter.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Used to authenticate with the OpenAI service, instead of Azure OpenAI.
         * This automatically sets the endpoint to https://api.openai.com/v1.
         *
         * @param nonAzureApiKey The non-Azure OpenAI API key
         * @return builder
         */
        public Builder nonAzureApiKey(String nonAzureApiKey) {
            this.keyCredential = new KeyCredential(nonAzureApiKey);
            this.endpoint = "https://api.openai.com/v1";
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         *
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * Sets the {@code HttpClientProvider} to use for creating the HTTP client to communicate with the OpenAI api.
         *
         * @param httpClientProvider The {@code HttpClientProvider} to use
         * @return builder
         */
        public Builder httpClientProvider(HttpClientProvider httpClientProvider) {
            this.httpClientProvider = httpClientProvider;
            return this;
        }

        /**
         * Sets the deployment name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param deploymentName The Deployment name.
         * @return builder
         */
        public Builder deploymentName(String deploymentName) {
            this.deploymentName = deploymentName;
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

        public Builder proxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * Sets the Azure OpenAI client. This is an optional parameter, if you need more flexibility than using the endpoint, serviceVersion, apiKey, deploymentName parameters.
         *
         * @param openAIClient The Azure OpenAI client.
         * @return builder
         */
        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        public Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public AzureOpenAiEmbeddingModel build() {
            return new AzureOpenAiEmbeddingModel(this);
        }
    }
}
