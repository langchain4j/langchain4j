package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel;
import dev.langchain4j.model.sparkdesk.client.image.*;
import dev.langchain4j.model.sparkdesk.client.message.UserMessage;
import dev.langchain4j.model.sparkdesk.shared.RequestHeader;
import dev.langchain4j.model.sparkdesk.shared.RequestMessage;
import lombok.Builder;

import java.util.Collections;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Represents an Sparkdesk image model,detail https://www.xfyun.cn/doc/spark/ImageGeneration.html
 */
public class SparkdeskAiImageModel implements ImageModel {

    private final String appId;
    private final Ratio ratio;
    private final Integer maxRetries;
    private final SparkdeskAiHttpClient client;

    @Builder
    public SparkdeskAiImageModel(
            String baseUrl,
            String appId,
            String apiKey,
            String apiSecret,
            Ratio ratio,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.ratio = getOrDefault(ratio, Ratio.WIDTH_512_HEIGHT_512);
        this.appId = SparkUtils.isNullOrEmpty(appId, "The appId field cannot be null or an empty string");
        this.client = SparkdeskAiHttpClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://spark-api.cn-huabei-1.xf-yun.com"))
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    @Override
    public Response<Image> generate(String prompt) {
        ImageRequest request = ImageRequest.builder()
                .header(RequestHeader.builder().appId(this.appId).build())
                .parameter(ImageParameter.builder().chat(ImageChat.builder()
                        .domain(ChatCompletionModel.SPARK_PRO.toString())
                        .width(this.ratio.getWidth())
                        .height(this.ratio.getHeight())
                        .build()).build())
                .payload(ImagePayload.builder().message(RequestMessage.builder()
                        .text(Collections.singletonList(UserMessage.from(prompt)))
                        .build()).build())
                .build();
        ImageResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries);
        if (response == null) {
            return Response.from(Image.builder().build());
        }
        return Response.from(
                Image.builder()
                        .base64Data(response.getPayload().getChoices().getText().get(0).getContent())
                        .build()
        );
    }
}
