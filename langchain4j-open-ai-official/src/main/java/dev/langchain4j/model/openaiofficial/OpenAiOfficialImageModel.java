package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.credential.Credential;
import com.openai.models.ImageGenerateParams;
import com.openai.models.ImagesResponse;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openaiofficial.spi.OpenAiOfficialImageModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.setupSyncClient;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class OpenAiOfficialImageModel implements ImageModel {

    private final OpenAIClient client;
    private final boolean useAzure;
    private final String modelName;
    private final ImageGenerateParams.Size size;
    private final ImageGenerateParams.Quality quality;
    private final ImageGenerateParams.Style style;
    private final String user;
    private final Duration timeout;
    private final ImageGenerateParams.ResponseFormat responseFormat;

    public OpenAiOfficialImageModel( String baseUrl,
                                     String apiKey,
                                     String azureApiKey,
                                     Credential credential,
                                     String azureDeploymentName,
                                     AzureOpenAIServiceVersion azureOpenAIServiceVersion,
                                     String organizationId,
                                     String modelName,
                                     ImageGenerateParams.Size size,
                                     ImageGenerateParams.Quality quality,
                                     ImageGenerateParams.Style style,
                                     String user,
                                     ImageGenerateParams.ResponseFormat responseFormat,
                                     Duration timeout,
                                     Integer maxRetries,
                                     Proxy proxy,
                                     Map<String, String> customHeaders) {

        if (azureApiKey != null || credential != null) {
            // Using Azure OpenAI
            this.useAzure = true;
            ensureNotBlank(modelName, "modelName");
        } else {
            // Using OpenAI
            this.useAzure = false;
        }

        this.client = setupSyncClient(
                baseUrl,
                useAzure,
                apiKey,
                azureApiKey,
                credential,
                azureDeploymentName,
                azureOpenAIServiceVersion,
                organizationId,
                modelName,
                timeout,
                maxRetries,
                proxy,
                customHeaders);

        this.modelName = modelName;
        this.size = getOrDefault(size, ImageGenerateParams.Size._1024X1024);
        this.quality = getOrDefault(quality, ImageGenerateParams.Quality.STANDARD);
        this.style = getOrDefault(style, ImageGenerateParams.Style.NATURAL);
        this.user = user;
        this.timeout = timeout;
        this.responseFormat = getOrDefault(responseFormat, ImageGenerateParams.ResponseFormat.URL);
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {

        ImageGenerateParams imageGenerateParams =
                impageGenerateParamsBuilder(prompt)
                        .build();

        ImagesResponse response = client.images()
                .generate(imageGenerateParams, requestOptions());

        return Response.from(fromOpenAiImage(response.data().get(0)));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {

        ImageGenerateParams imageGenerateParams =
                impageGenerateParamsBuilder(prompt)
                        .n(n)
                        .build();

        ImagesResponse response = client.images()
                .generate(imageGenerateParams, requestOptions());

        return Response.from(
                response.data()
                        .stream()
                        .map(OpenAiOfficialImageModel::fromOpenAiImage)
                        .toList());
    }

    private ImageGenerateParams.Builder impageGenerateParamsBuilder(String prompt) {
        ImageGenerateParams.Builder builder = ImageGenerateParams.builder();
        builder.model(modelName)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .style(style)
                .responseFormat(responseFormat);

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

    private static Image fromOpenAiImage(com.openai.models.Image openAiImage) {
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

    public static OpenAiOfficialImageModelBuilder builder() {
        for (OpenAiOfficialImageModelBuilderFactory factory : loadFactories(OpenAiOfficialImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiOfficialImageModelBuilder();
    }

    public static class OpenAiOfficialImageModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String azureApiKey;
        private Credential credential;
        private String azureDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
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

        public OpenAiOfficialImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiOfficialImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiOfficialImageModelBuilder azureApiKey(String azureApiKey) {
            this.azureApiKey = azureApiKey;
            return this;
        }

        public OpenAiOfficialImageModelBuilder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public OpenAiOfficialImageModelBuilder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        public OpenAiOfficialImageModelBuilder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public OpenAiOfficialImageModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiOfficialImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiOfficialImageModelBuilder modelName(com.openai.models.ImageModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiOfficialImageModelBuilder size(String size) {
            this.size = ImageGenerateParams.Size.of(size);
            return this;
        }

        public OpenAiOfficialImageModelBuilder size(ImageGenerateParams.Size size) {
            this.size = size;
            return this;
        }

        public OpenAiOfficialImageModelBuilder quality(String quality) {
            this.quality = ImageGenerateParams.Quality.of(quality);
            return this;
        }

        public OpenAiOfficialImageModelBuilder quality(ImageGenerateParams.Quality quality) {
            this.quality = quality;
            return this;
        }

        public OpenAiOfficialImageModelBuilder style(String style) {
            this.style = ImageGenerateParams.Style.of(style);
            return this;
        }

        public OpenAiOfficialImageModelBuilder style(ImageGenerateParams.Style style) {
            this.style = style;
            return this;
        }

        public OpenAiOfficialImageModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiOfficialImageModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = ImageGenerateParams.ResponseFormat.of(responseFormat);
            return this;
        }

        public OpenAiOfficialImageModelBuilder responseFormat(ImageGenerateParams.ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiOfficialImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiOfficialImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiOfficialImageModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiOfficialImageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiOfficialImageModel build() {

            return new OpenAiOfficialImageModel(
                    baseUrl,
                    apiKey,
                    azureApiKey,
                    credential,
                    azureDeploymentName,
                    azureOpenAIServiceVersion,
                    organizationId,
                    modelName,
                    size,
                    quality,
                    style,
                    user,
                    responseFormat,
                    timeout,
                    maxRetries,
                    proxy,
                    customHeaders);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OpenAiOfficialEmbeddingModel.OpenAiOfficialEmbeddingModelBuilder.class.getSimpleName() + "[", "]")
                    .add("baseUrl='" + baseUrl + "'")
                    .add("credential=" + credential)
                    .add("azureDeploymentName='" + azureDeploymentName + "'")
                    .add("azureOpenAIServiceVersion=" + azureOpenAIServiceVersion)
                    .add("organizationId='" + organizationId + "'")
                    .add("modelName='" + modelName + "'")
                    .add("size='" + size + "'")
                    .add("quality='" + quality + "'")
                    .add("style='" + style + "'")
                    .add("user='" + user + "'")
                    .add("responseFormat='" + responseFormat + "'")
                    .add("timeout=" + timeout)
                    .add("maxRetries=" + maxRetries)
                    .add("proxy=" + proxy)
                    .add("customHeaders=" + customHeaders)
                    .toString();
        }
    }
}
