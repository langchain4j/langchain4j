package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.image.ImageModelName;
import dev.langchain4j.model.zhipu.image.ImageRequest;
import dev.langchain4j.model.zhipu.image.ImageResponse;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class ZhipuAiImageModel implements ImageModel {

    private final String model;
    private final String userId;
    private final Integer maxRetries;
    private final ZhipuAiClient client;

    /**
     * Instantiates ZhipuAi cogview-3 image processing model.
     * Find the parameters description <a href="https://open.bigmodel.cn/dev/api#cogview">here</a>.
     *
     * @param model  cogview-3 is default
     * @param userId A unique identifier representing your end-user, which can help ZhipuAI to monitor
     *               and detect abuse. User ID length requirement: minimum of 6 characters, maximum of
     *               128 characters
     */
    public ZhipuAiImageModel(
            String model,
            String userId,
            String apiKey,
            String baseUrl,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout
    ) {
        this.model = getOrDefault(model, ImageModelName.COGVIEW_3.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.userId = userId;
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAiImageModelBuilder builder() {
        return new ZhipuAiImageModelBuilder();
    }

    @Override
    public Response<Image> generate(String prompt) {
        ImageRequest request = ImageRequest.builder()
                .prompt(prompt)
                .userId(this.userId)
                .model(this.model)
                .build();
        ImageResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries);
        if (response == null) {
            return Response.from(Image.builder().build());
        }
        return Response.from(
                Image.builder()
                        .url(response.getData().get(0).getUrl())
                        .build()
        );
    }

    public static class ZhipuAiImageModelBuilder {
        private String model;
        private String userId;
        private String apiKey;
        private String baseUrl;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        ZhipuAiImageModelBuilder() {
        }

        public ZhipuAiImageModelBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ZhipuAiImageModelBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public ZhipuAiImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ZhipuAiImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiImageModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAiImageModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAiImageModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public ZhipuAiImageModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiImageModel build() {
            return new ZhipuAiImageModel(this.model, this.userId, this.apiKey, this.baseUrl, this.maxRetries, this.logRequests, this.logResponses, this.callTimeout, this.connectTimeout, this.readTimeout, this.writeTimeout);
        }
    }
}
