package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.OpenAiModelName.DALL_E_2;
import static java.time.Duration.ofSeconds;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.image.GenerateImagesRequest;
import dev.ai4j.openai4j.image.GenerateImagesResponse;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.NonNull;

/**
 * Represents an OpenAI DALL·E models to generate artistic images. Versions 2 and 3 (default) are supported.
 */
public class OpenAiImageModel implements ImageModel {

    private final String model;
    private final String size;
    private final String quality;
    private final String style;
    private final String user;
    private final String responseFormat;

    private final OpenAiClient client;

    private final Integer maxRetries;

    /**
     * Instantiates OpenAI DALL·E image processing model.
     * Find the parameters description <a href="https://platform.openai.com/docs/api-reference/images/create">here</a>.
     *
     * @param model          dall-e-3 is default one
     * @param persistTo      specifies the local path where the generated image will be downloaded to (in case provided).
     *                       The URL within <code>dev.ai4j.openai4j.image.GenerateImagesResponse</code> will contain
     *                       the URL to local images then.
     * @param withPersisting generated response will be persisted under <code>java.io.tmpdir</code>.
     *                       The URL within <code>dev.ai4j.openai4j.image.GenerateImagesResponse</code> will contain
     *                       the URL to local images then.
     */
    @Builder
    @SuppressWarnings("rawtypes")
    public OpenAiImageModel(
        @NonNull String apiKey,
        String model,
        String size,
        String quality,
        String style,
        String user,
        String responseFormat,
        Duration timeout,
        Integer maxRetries,
        Proxy proxy,
        Boolean logRequests,
        Boolean logResponses,
        Boolean withPersisting,
        Path persistTo
    ) {
        timeout = getOrDefault(timeout, ofSeconds(60));

        OpenAiClient.Builder cBuilder = OpenAiClient
            .builder()
            .openAiApiKey(apiKey)
            .callTimeout(timeout)
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .proxy(proxy)
            .logRequests(getOrDefault(logRequests, false))
            .logResponses(getOrDefault(logResponses, false))
            .persistTo(persistTo);

        if (withPersisting != null && withPersisting) {
            cBuilder.withPersisting();
        }

        this.client = cBuilder.build();

        this.maxRetries = getOrDefault(maxRetries, 3);
        this.model = model;
        this.size = size;
        this.quality = quality;
        this.style = style;
        this.user = user;
        this.responseFormat = responseFormat;
    }

    @Override
    public Response<Image> generate(String prompt) {
        GenerateImagesRequest request = requestBuilder(prompt).build();

        GenerateImagesResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries).execute();

        return Response.from(fromImageData(response.data().get(0)));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        GenerateImagesRequest request = requestBuilder(prompt).n(n).build();

        GenerateImagesResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries).execute();

        return Response.from(
            response.data().stream().map(OpenAiImageModel::fromImageData).collect(Collectors.toList())
        );
    }

    public static class OpenAiImageModelBuilder {

        public OpenAiImageModelBuilder withPersisting() {
            withPersisting = true;
            return this;
        }

        public OpenAiImageModelBuilder withApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
    }

    private static Image fromImageData(GenerateImagesResponse.ImageData data) {
        return Image.builder().url(data.url()).base64Data(data.b64Json()).revisedPrompt(data.revisedPrompt()).build();
    }

    private GenerateImagesRequest.Builder requestBuilder(String prompt) {
        GenerateImagesRequest.Builder requestBuilder = GenerateImagesRequest
            .builder()
            .prompt(prompt)
            .size(size)
            .quality(quality)
            .style(style)
            .user(user)
            .responseFormat(responseFormat);

        if (DALL_E_2.equals(model)) {
            requestBuilder.model(dev.ai4j.openai4j.image.ImageModel.DALL_E_2);
        }

        return requestBuilder;
    }
}
