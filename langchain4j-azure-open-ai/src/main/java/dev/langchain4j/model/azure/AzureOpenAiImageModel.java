package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.azure.spi.AzureOpenAiImageModelBuilderFactory;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.spi.ServiceHelper;

import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.imageFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupOpenAIClient;

/**
 * Represents an OpenAI image model, hosted on Azure, such as dall-e-3.
 * <p>
 * You can find a tutorial on using Azure OpenAI to generate images at: https://learn.microsoft.com/en-us/azure/ai-services/openai/dall-e-quickstart?pivots=programming-language-java
 * <p>
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apikey (or an alternate authentication method, see below for more information) and deploymentName.
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
 * - This requires to add the `com.azure:azure-identity` dependency to your project, which is an optional dependency to this library.
 * - You need to provide a TokenCredential instance, using the tokenCredential() method in the Builder, or the tokenCredential parameter in the constructor.
 * As an example, DefaultAzureCredential can be used to authenticate the client: Set the values of the client ID, tenant ID, and
 * client secret of the AAD application as environment variables: AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_CLIENT_SECRET.
 * Then, provide the DefaultAzureCredential instance to the builder: `builder.tokenCredential(new DefaultAzureCredentialBuilder().build())`.
 */
public class AzureOpenAiImageModel implements ImageModel {

    private OpenAIClient client;
    private final String deploymentName;
    private ImageGenerationQuality quality = null;
    private ImageSize size = null;
    private String user = null;
    private ImageGenerationStyle style = null;
    private ImageGenerationResponseFormat responseFormat = null;

    public AzureOpenAiImageModel(OpenAIClient client,
                                 String deploymentName,
                                 String quality,
                                 String size,
                                 String user,
                                 String style,
                                 String responseFormat) {

        this(deploymentName, quality, size, user, style, responseFormat);
        this.client = client;
    }

    public AzureOpenAiImageModel(String endpoint,
                                String serviceVersion,
                                String apiKey,
                                String deploymentName,
                                String quality,
                                String size,
                                String user,
                                String style,
                                String responseFormat,
                                Duration timeout,
                                Integer maxRetries,
                                ProxyOptions proxyOptions,
                                boolean logRequestsAndResponses) {

        this(deploymentName, quality, size, user, style, responseFormat);
        this.client = setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    public AzureOpenAiImageModel(String endpoint,
                                 String serviceVersion,
                                 KeyCredential keyCredential,
                                 String deploymentName,
                                 String quality,
                                 String size,
                                 String user,
                                 String style,
                                 String responseFormat,
                                 Duration timeout,
                                 Integer maxRetries,
                                 ProxyOptions proxyOptions,
                                 boolean logRequestsAndResponses) {

        this(deploymentName, quality, size, user, style, responseFormat);
        this.client = setupOpenAIClient(endpoint, serviceVersion, keyCredential, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    public AzureOpenAiImageModel(String endpoint,
                                 String serviceVersion,
                                 TokenCredential tokenCredential,
                                 String deploymentName,
                                 String quality,
                                 String size,
                                 String user,
                                 String style,
                                 String responseFormat,
                                 Duration timeout,
                                 Integer maxRetries,
                                 ProxyOptions proxyOptions,
                                 boolean logRequestsAndResponses) {

        this(deploymentName, quality, size, user, style, responseFormat);
        this.client = setupOpenAIClient(endpoint, serviceVersion, tokenCredential, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    private AzureOpenAiImageModel(String deploymentName, String quality, String size, String user, String style, String responseFormat) {
        this.deploymentName = getOrDefault(deploymentName, "dall-e-3");
        if (quality != null) {
            this.quality = ImageGenerationQuality.fromString(quality);
        }
        if (size != null) {
            this.size = ImageSize.fromString(size);
        }
        if (user != null) {
            this.user = user;
        }
        if (style != null) {
            this.style = ImageGenerationStyle.fromString(style);
        }
        if (responseFormat != null) {
            this.responseFormat = ImageGenerationResponseFormat.fromString(responseFormat);
        }
    }

    @Override
    public Response<Image> generate(String prompt) {
        ImageGenerationOptions options = new ImageGenerationOptions(prompt)
                .setModel(deploymentName)
                .setN(1)
                .setQuality(quality)
                .setSize(size)
                .setUser(user)
                .setStyle(style)
                .setResponseFormat(responseFormat);

        ImageGenerations imageGenerations = client.getImageGenerations(deploymentName, options);
        Image image = imageFrom(imageGenerations.getData().get(0));
        return Response.from(image);
    }

    public static Builder builder() {
        return ServiceHelper.loadFactoryService(
                AzureOpenAiImageModelBuilderFactory.class,
                Builder::new
        );
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private String deploymentName;
        private String quality;
        private String size;
        private String user;
        private String style;
        private String responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;

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
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
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

        /**
         * Sets the quality of the image. This is an optional parameter.
         *
         * @param quality The quality of the image.
         * @return builder
         */
        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        /**
         * Sets the quality of the image, using the ImageGenerationQuality enum. This is an optional parameter.
         *
         * @param imageGenerationQuality The quality of the image.
         * @return builder
         */
        public Builder quality(ImageGenerationQuality imageGenerationQuality) {
            this.quality = imageGenerationQuality.toString();
            return this;
        }

        /**
         * Sets the size of the image. This is an optional parameter.
         *
         * @param size The size of the image.
         * @return builder
         */
        public Builder size(String size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the size of the image, using the ImageSize enum. This is an optional parameter.
         *
         * @param imageSize The size of the image.
         * @return builder
         */
        public Builder size(ImageSize imageSize) {
            this.size = imageSize.toString();
            return this;
        }

        /**
         * Sets the user of the image. This is an optional parameter.
         *
         * @param user The user of the image.
         * @return builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the style of the image. This is an optional parameter.
         *
         * @param style The style of the image.
         * @return builder
         */
        public Builder style(String style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the style of the image, using the ImageGenerationStyle enum. This is an optional parameter.
         *
         * @param imageGenerationStyle The style of the image.
         * @return builder
         */
        public Builder style(ImageGenerationStyle imageGenerationStyle) {
            this.style = imageGenerationStyle.toString();
            return this;
        }

        /**
         * Sets the response format of the image. This is an optional parameter.
         *
         * @param responseFormat The response format of the image.
         * @return builder
         */
        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Sets the response format of the image, using the ImageGenerationResponseFormat enum. This is an optional parameter.
         *
         * @param imageGenerationResponseFormat The response format of the image.
         * @return builder
         */
        public Builder responseFormat(ImageGenerationResponseFormat imageGenerationResponseFormat) {
            this.responseFormat = imageGenerationResponseFormat.toString();
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

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public AzureOpenAiImageModel build() {
            if (openAIClient == null) {
                if (tokenCredential != null) {
                    return new AzureOpenAiImageModel(
                            endpoint,
                            serviceVersion,
                            tokenCredential,
                            deploymentName,
                            quality,
                            size,
                            user,
                            style,
                            responseFormat,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses);
                } else if (keyCredential != null) {
                    return new AzureOpenAiImageModel(
                            endpoint,
                            serviceVersion,
                            keyCredential,
                            deploymentName,
                            quality,
                            size,
                            user,
                            style,
                            responseFormat,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses);
                }
                return new AzureOpenAiImageModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        quality,
                        size,
                        user,
                        style,
                        responseFormat,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses);
            }
            return new AzureOpenAiImageModel(
                    openAIClient,
                    deploymentName,
                    quality,
                    size,
                    user,
                    style,
                    responseFormat);
        }
    }
}
