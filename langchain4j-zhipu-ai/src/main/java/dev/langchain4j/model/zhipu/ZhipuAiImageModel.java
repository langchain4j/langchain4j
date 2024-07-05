package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.image.ImageModelName;
import dev.langchain4j.model.zhipu.image.ImageRequest;
import dev.langchain4j.model.zhipu.image.ImageResponse;
import lombok.Builder;

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
    @Builder
    public ZhipuAiImageModel(
            String model,
            String userId,
            String apiKey,
            String baseUrl,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.model = getOrDefault(model, ImageModelName.COGVIEW_3.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.userId = userId;
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
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
}
