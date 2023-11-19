package dev.langchain4j.image.openai.dalle;

import dev.langchain4j.data.image.Image;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import dev.langchain4j.model.chat.ImageProcessingModel;
import dev.langchain4j.model.output.Response;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.message.ChatMessage;

import java.net.Proxy;
import java.time.Duration;

import lombok.Builder;
import lombok.NonNull;

public class OpenAiDalleImageProcessingModel implements ImageProcessingModel {

    public static final String DALL_E_2 = "dall-e-2"; // onyone still needs that? :)
    public static final String DALL_E_3 = "dall-e-3"; // default one
    public static final String DALL_E_SIZE_256_x_256 = "256x256"; // for 2 only
    public static final String DALL_E_SIZE_512_x_512 = "512x512"; // for 2 only
    public static final String DALL_E_SIZE_1024_x_1024 = "1024x1024"; // for 2 & 3
    public static final String DALL_E_SIZE_1792_x_1024 = "1792x1024"; // for 3 only
    public static final String DALL_E_SIZE_1024_x_1792 = "1024x1792"; // for 3 only
    public static final String DALL_E_QUALITY_STANDARD = "standard";
    public static final String DALL_E_QUALITY_HD = "hd";
    public static final String DALL_E_STYLE_VIVID = "vivid";
    public static final String DALL_E_STYLE_NATURAL = "natural";

    private final String model;
    private final String size;
    private final String quality;
    private final String style;
    private final String downloadFolder;

    private final OpenAiDalleClient client;

    private final Integer maxRetries;

    @Builder
    public OpenAiDalleImageProcessingModel(
            @NonNull String apiKey,
            String model,
            String size,
            String quality,
            String style,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            Boolean logRequests,
            Boolean logResponses,
            String downloadFolder
    ) {
        this.client =
                OpenAiDalleClient
                        .builder()
                        .openAiApiKey(apiKey)
                        .callTimeout(timeout)
                        .connectTimeout(timeout)
                        .readTimeout(timeout)
                        .writeTimeout(timeout)
                        .proxy(proxy)
                        .logRequests(logRequests)
                        .logResponses(logResponses)
                        .build();

        this.maxRetries = getOrDefault(maxRetries, 3);
        this.model = getOrDefault(model, DALL_E_3);
        this.size = size;
        this.quality = quality;
        this.style = style;
        this.downloadFolder = downloadFolder;
    }

    @Override
    public Response<Image> generate(String prompt) {
        OpenAiDalleRequest request = OpenAiDalleRequest.builder().prompt(prompt).size(size).model(model).quality(quality).style(style).build();

        OpenAiDalleResponse response = withRetry(() -> client.generate(request), maxRetries);
        String url = response.getData().get(0).getUrl();
        if (downloadFolder != null) {
            url = OpenAiImageDownloader.downloadFile(url, downloadFolder);
        }
        Image image = Image.builder().url(url).build();

        return Response.from(image);
    }
}
