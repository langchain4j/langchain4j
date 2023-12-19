package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;

import java.nio.file.Path;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.imageFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupOpenAIClient;

/**
 * Represents an OpenAI image model, hosted on Azure, such as dall-e-3.
 * <p>
 * You can find a tutorial on using Azure OpenAI to generate images at: https://learn.microsoft.com/en-us/azure/ai-services/openai/dall-e-quickstart?pivots=programming-language-java
 * <p>
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apiKey and deploymentName.
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header as follows: `api-key: OPENAI_API_KEY`Y`
 * <p>
 * 2. Azure Active Directory Authentication: For this type of authentication, HTTP requests must include the
 * authentication/access token in the "Authorization" HTTP header.
 * <p>
 * <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/reference">More information</a>
 * <p>
 * Please note, that currently, only API Key authentication is supported by this class,
 * second authentication option will be supported later.
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
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
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
         * Sets the Azure OpenAI API key. This is a mandatory parameter.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
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
            if (openAIClient != null) {
                return new AzureOpenAiImageModel(
                        openAIClient,
                        deploymentName,
                        quality,
                        size,
                        user,
                        style,
                        responseFormat);
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
    }
}
