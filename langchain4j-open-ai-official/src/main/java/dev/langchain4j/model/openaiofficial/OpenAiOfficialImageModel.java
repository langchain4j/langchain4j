package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.detectModelHost;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.setupSyncClient;

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

public class OpenAiOfficialImageModel implements ImageModel {

    private final OpenAIClient client;
    private final String modelName;
    private InternalOpenAiOfficialHelper.ModelHost modelHost;
    private final ImageGenerateParams.Size size;
    private final ImageGenerateParams.Quality quality;
    private final ImageGenerateParams.Style style;
    private final String user;
    private final Duration timeout;
    private final ImageGenerateParams.ResponseFormat responseFormat;

    public OpenAiOfficialImageModel(Builder builder) {

        this.modelHost = detectModelHost(
                builder.isAzure,
                builder.isGitHubModels,
                builder.baseUrl,
                builder.azureDeploymentName,
                builder.azureOpenAIServiceVersion);

        this.client = setupSyncClient(
                builder.baseUrl,
                builder.apiKey,
                builder.credential,
                builder.azureDeploymentName,
                builder.azureOpenAIServiceVersion,
                builder.organizationId,
                this.modelHost,
                builder.openAIClient,
                builder.modelName,
                builder.timeout,
                builder.maxRetries,
                builder.proxy,
                builder.customHeaders);

        this.modelName = builder.modelName;
        this.size = builder.size;
        this.quality = builder.quality;
        this.style = builder.style;
        this.user = builder.user;
        this.timeout = builder.timeout;
        this.responseFormat = builder.responseFormat;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {

        ImageGenerateParams imageGenerateParams =
                impageGenerateParamsBuilder(prompt).build();

        ImagesResponse response = client.images().generate(imageGenerateParams, requestOptions());

        if (response.data().isEmpty() && response.data().get().isEmpty()) {
            throw new IllegalArgumentException("Image generation failed: no image returned");
        }
        return Response.from(fromOpenAiImage(response.data().get().get(0)));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {

        ImageGenerateParams imageGenerateParams =
                impageGenerateParamsBuilder(prompt).n(n).build();

        ImagesResponse response = client.images().generate(imageGenerateParams, requestOptions());

        if (response.data().isEmpty()) {
            throw new IllegalArgumentException("Image generation failed: no image returned");
        }

        return Response.from(response.data().get().stream()
                .map(OpenAiOfficialImageModel::fromOpenAiImage)
                .toList());
    }

    private ImageGenerateParams.Builder impageGenerateParamsBuilder(String prompt) {
        ImageGenerateParams.Builder builder = ImageGenerateParams.builder();
        builder.model(modelName);
        builder.prompt(prompt);

        if (size != null) {
            builder.size(size);
        }
        if (quality != null) {
            builder.quality(quality);
        }
        if (style != null) {
            builder.style(style);
        }
        if (responseFormat != null) {
            builder.responseFormat(responseFormat);
        }
        if (user != null) {
            builder.user(user);
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

    private static Image fromOpenAiImage(com.openai.models.images.Image openAiImage) {
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
        return imageBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Credential credential;
        private String azureDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isAzure;
        private boolean isGitHubModels;
        private OpenAIClient openAIClient;
        private String modelName;
        private ImageGenerateParams.Size size;
        private ImageGenerateParams.Quality quality;
        private ImageGenerateParams.Style style;
        private String user;
        private ImageGenerateParams.ResponseFormat responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Map<String, String> customHeaders;

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

        public Builder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
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

        public Builder isAzure(boolean isAzure) {
            this.isAzure = isAzure;
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

        public Builder style(String style) {
            this.style = ImageGenerateParams.Style.of(style);
            return this;
        }

        public Builder style(ImageGenerateParams.Style style) {
            this.style = style;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = ImageGenerateParams.ResponseFormat.of(responseFormat);
            return this;
        }

        public Builder responseFormat(ImageGenerateParams.ResponseFormat responseFormat) {
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
