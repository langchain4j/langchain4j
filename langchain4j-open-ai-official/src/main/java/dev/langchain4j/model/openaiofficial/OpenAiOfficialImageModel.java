package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.credential.Credential;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImagesResponse;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Represents an OpenAI image generation model.
 * Find the parameters description <a href="https://developers.openai.com/api/reference/resources/images/methods/generate">here</a>.
 */
public class OpenAiOfficialImageModel implements ImageModel {

    private final OpenAIClient client;
    private final String modelName;
    private final ImageGenerateParams.Size size;
    private final ImageGenerateParams.Quality quality;
    private final String user;
    private final Duration timeout;
    private final ImageGenerateParams.Background background;
    private final ImageGenerateParams.OutputFormat outputFormat;
    private final Long outputCompression;
    private final ImageGenerateParams.Moderation moderation;

    public OpenAiOfficialImageModel(Builder builder) {

        if (builder.openAIClient != null) {
            this.client = builder.openAIClient;
        } else {
            this.client = setupSyncClient(
                    builder.baseUrl,
                    builder.apiKey,
                    builder.credential,
                    builder.microsoftFoundryDeploymentName,
                    builder.azureOpenAIServiceVersion,
                    builder.organizationId,
                    builder.isMicrosoftFoundry,
                    builder.isGitHubModels,
                    builder.modelName,
                    builder.timeout,
                    builder.maxRetries,
                    builder.proxy,
                    builder.customHeaders);
        }

        this.modelName = builder.modelName;
        this.size = builder.size;
        this.quality = builder.quality;
        this.user = builder.user;
        this.timeout = builder.timeout;
        this.background = builder.background;
        this.outputFormat = builder.outputFormat;
        this.outputCompression = builder.outputCompression;
        this.moderation = builder.moderation;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {

        ImageGenerateParams imageGenerateParams =
                imageGenerateParamsBuilder(prompt).build();

        ImagesResponse response = client.images().generate(imageGenerateParams, requestOptions());

        if (response.data().isEmpty() || response.data().get().isEmpty()) {
            throw new IllegalArgumentException("Image generation failed: no image returned");
        }

        String mimeType = response.outputFormat().map(of -> "image/" + of).orElse(null);
        return Response.from(fromOpenAiImage(response.data().get().get(0), mimeType));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {

        ImageGenerateParams imageGenerateParams =
                imageGenerateParamsBuilder(prompt).n(n).build();

        ImagesResponse response = client.images().generate(imageGenerateParams, requestOptions());

        if (response.data().isEmpty()) {
            throw new IllegalArgumentException("Image generation failed: no image returned");
        }

        String mimeType = response.outputFormat().map(of -> "image/" + of).orElse(null);
        return Response.from(response.data().get().stream()
                .map(img -> fromOpenAiImage(img, mimeType))
                .toList());
    }

    private ImageGenerateParams.Builder imageGenerateParamsBuilder(String prompt) {
        ImageGenerateParams.Builder builder = ImageGenerateParams.builder();
        builder.model(modelName);
        builder.prompt(prompt);

        if (size != null) {
            builder.size(size);
        }
        if (quality != null) {
            builder.quality(quality);
        }
        if (user != null) {
            builder.user(user);
        }
        if (background != null) {
            builder.background(background);
        }
        if (outputFormat != null) {
            builder.outputFormat(outputFormat);
        }
        if (outputCompression != null) {
            builder.outputCompression(outputCompression);
        }
        if (moderation != null) {
            builder.moderation(moderation);
        }
        return builder;
    }

    private RequestOptions requestOptions() {
        RequestOptions.Builder builder = new RequestOptions.Builder();
        if (timeout != null) {
            builder.timeout(timeout);
        }
        return builder.build();
    }

    private static Image fromOpenAiImage(com.openai.models.images.Image openAiImage, String mimeType) {
        Image.Builder imageBuilder = Image.builder();

        if (openAiImage.url().isPresent()) {
            imageBuilder.url(openAiImage.url().get());
        } else if (openAiImage.b64Json().isPresent()) {
            imageBuilder.base64Data(openAiImage.b64Json().get());
        } else {
            throw new IllegalArgumentException("Image must have either a URL or base64 data");
        }

        if (openAiImage.revisedPrompt().isPresent()) {
            imageBuilder.revisedPrompt(openAiImage.revisedPrompt().get());
        }

        if (mimeType != null) {
            imageBuilder.mimeType(mimeType);
        }

        return imageBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Credential credential;
        private String microsoftFoundryDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isMicrosoftFoundry;
        private boolean isGitHubModels;
        private OpenAIClient openAIClient;
        private String modelName;
        private ImageGenerateParams.Size size;
        private ImageGenerateParams.Quality quality;
        private String user;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Map<String, String> customHeaders;
        private ImageGenerateParams.Background background;
        private ImageGenerateParams.OutputFormat outputFormat;
        private Long outputCompression;
        private ImageGenerateParams.Moderation moderation;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        /**
         * @deprecated Use {@link #microsoftFoundryDeploymentName(String)} instead
         */
        @Deprecated
        public Builder azureDeploymentName(String azureDeploymentName) {
            this.microsoftFoundryDeploymentName = azureDeploymentName;
            return this;
        }

        public Builder microsoftFoundryDeploymentName(String microsoftFoundryDeploymentName) {
            this.microsoftFoundryDeploymentName = microsoftFoundryDeploymentName;
            return this;
        }

        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * @deprecated Use {@link #isMicrosoftFoundry(boolean)} instead
         */
        @Deprecated
        public Builder isAzure(boolean isAzure) {
            this.isMicrosoftFoundry = isAzure;
            return this;
        }

        public Builder isMicrosoftFoundry(boolean isMicrosoftFoundry) {
            this.isMicrosoftFoundry = isMicrosoftFoundry;
            return this;
        }

        public Builder isGitHubModels(boolean isGitHubModels) {
            this.isGitHubModels = isGitHubModels;
            return this;
        }

        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(com.openai.models.images.ImageModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder size(String size) {
            this.size = ImageGenerateParams.Size.of(size);
            return this;
        }

        public Builder size(ImageGenerateParams.Size size) {
            this.size = size;
            return this;
        }

        public Builder quality(String quality) {
            this.quality = ImageGenerateParams.Quality.of(quality);
            return this;
        }

        public Builder quality(ImageGenerateParams.Quality quality) {
            this.quality = quality;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder background(String background) {
            this.background = ImageGenerateParams.Background.of(background);
            return this;
        }

        public Builder background(ImageGenerateParams.Background background) {
            this.background = background;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = ImageGenerateParams.OutputFormat.of(outputFormat);
            return this;
        }

        public Builder outputFormat(ImageGenerateParams.OutputFormat outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public Builder outputCompression(Long outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        public Builder moderation(String moderation) {
            this.moderation = ImageGenerateParams.Moderation.of(moderation);
            return this;
        }

        public Builder moderation(ImageGenerateParams.Moderation moderation) {
            this.moderation = moderation;
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

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiOfficialImageModel build() {
            return new OpenAiOfficialImageModel(this);
        }
    }
}
