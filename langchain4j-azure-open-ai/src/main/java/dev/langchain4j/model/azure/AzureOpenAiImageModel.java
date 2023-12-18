package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupOpenAIClient;
import static java.util.stream.Collectors.toList;

public class AzureOpenAiImageModel implements ImageModel {

    private OpenAIClient client;
    private final String deploymentName;
    private final Integer n;
    private final ImageGenerationQuality quality;
    private final ImageSize size;
    private final String user;
    private final ImageGenerationStyle style;
    private final ImageGenerationResponseFormat responseFormat;

    public AzureOpenAiImageModel(OpenAIClient client,
                                 String deploymentName,
                                 Integer n,
                                 String quality,
                                 String size,
                                 String user,
                                 String style,
                                 String responseFormat) {

        this(deploymentName, n, quality, size, user, style, responseFormat);
        this.client = client;
    }

    public AzureOpenAiImageModel(String endpoint,
                                String serviceVersion,
                                String apiKey,
                                String deploymentName,
                                Integer n,
                                String quality,
                                String size,
                                String user,
                                String style,
                                String responseFormat,
                                Duration timeout,
                                Integer maxRetries,
                                ProxyOptions proxyOptions,
                                boolean logRequestsAndResponses) {

        this(deploymentName, n, quality, size, user, style, responseFormat);
        this.client = setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    private AzureOpenAiImageModel(String deploymentName, Integer n, String quality, String size, String user, String style, String responseFormat) {
        this.deploymentName = getOrDefault(deploymentName, "dall-e-3");
        this.n = getOrDefault(n, 1);
        this.quality = getOrDefault(ImageGenerationQuality.fromString(quality), ImageGenerationQuality.STANDARD);
        this.size = getOrDefault(ImageSize.fromString(size), ImageSize.SIZE1024X1024);
        this.user = getOrDefault(user, "");
        this.style = getOrDefault(ImageGenerationStyle.fromString(style), ImageGenerationStyle.NATURAL);
        this.responseFormat = getOrDefault(ImageGenerationResponseFormat.fromString(responseFormat), ImageGenerationResponseFormat.URL);
    }

    @Override
    public Response<List<Image>> generate(String prompt) {

        ImageGenerationOptions options = new ImageGenerationOptions(prompt)
                .setModel(deploymentName)
                .setN(n)
                .setQuality(quality)
                .setSize(size)
                .setUser(user)
                .setStyle(style)
                .setResponseFormat(responseFormat);

        ImageGenerations imageGenerations = client.getImageGenerations(deploymentName, options);

        List<Image> images = imageGenerations.getData().stream()
                .map(InternalAzureOpenAiHelper::imageFrom)
                .collect(toList());

        return Response.from(images);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private String deploymentName;
        private Integer n;
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
         * Sets the number of images to generate. This is an optional parameter.
         *
         * @param n The number of images to generate.
         * @return builder
         */
        public Builder setN(Integer n) {
            this.n = n;
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
         * Sets the response format of the image. This is an optional parameter.
         *
         * @param responseFormat The response format of the image.
         * @return builder
         */
        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
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
                        n,
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
                    n,
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
