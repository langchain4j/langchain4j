package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Background;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Moderation;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.OutputFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Quality;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.ResponseFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Size;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Style;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageService;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * An {@link ImageModel} implementation that integrates the IBM watsonx.ai Model Gateway with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ImageModel imageModel = WatsonxGatewayImageModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .modelName("gpt-image-1")
 *     .build();
 *
 * Image image = imageModel.generate("A futuristic city at sunset").content();
 * }</pre>
 *
 */
public class WatsonxGatewayImageModel implements ImageModel {

    private final ModelGatewayImageService modelGatewayImageService;
    private final ModelGatewayImageParameters defaultParameters;
    private final String modelName;
    private final String background;
    private final String moderation;
    private final Integer outputCompression;
    private final String outputFormat;
    private final String quality;
    private final String responseFormat;
    private final String size;
    private final String style;
    private final String user;

    private WatsonxGatewayImageModel(Builder builder) {

        var serviceBuilder = nonNull(builder.authenticator)
                ? ModelGatewayImageService.builder().authenticator(builder.authenticator)
                : ModelGatewayImageService.builder().apiKey(builder.apiKey);

        modelGatewayImageService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .modelId(builder.modelName)
                .version(builder.version)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();

        modelName = builder.modelName;
        background = builder.background;
        moderation = builder.moderation;
        outputCompression = builder.outputCompression;
        outputFormat = builder.outputFormat;
        quality = builder.quality;
        responseFormat = builder.responseFormat;
        size = builder.size;
        style = builder.style;
        user = builder.user;

        defaultParameters = parameters(null);
    }

    /**
     * Returns the id of the gateway model used to generate the images.
     *
     * @return the model id
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Generates a single image from the given prompt, using the parameters set on the builder.
     *
     * @param prompt the description of the image to generate
     * @return the generated image
     */
    @Override
    public Response<Image> generate(String prompt) {
        Response<List<Image>> response = generate(prompt, defaultParameters);
        return Response.from(response.content().get(0), response.tokenUsage());
    }

    /**
     * Generates {@code n} images from the given prompt, using the parameters set on the builder.
     *
     * @param prompt the description of the images to generate
     * @param n the number of images to generate, from 1 to 10
     * @return the generated images
     */
    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        return generate(prompt, parameters(n));
    }

    /**
     * Generates images from the given prompt, using the specified {@link ModelGatewayImageParameters}.
     *
     * <p>The given parameters replace the ones set on the builder, they are not merged with them.
     *
     * @param prompt the description of the images to generate
     * @param parameters the image parameters to use, or {@code null} to use the ones set on the builder
     * @return the generated images
     */
    public Response<List<Image>> generate(String prompt, ModelGatewayImageParameters parameters) {

        ModelGatewayImageResponse response = WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> modelGatewayImageService.generate(prompt, getOrDefault(parameters, defaultParameters)));

        return Converter.toImageResponse(response);
    }

    private ModelGatewayImageParameters parameters(Integer n) {
        return ModelGatewayImageParameters.builder()
                .background(background)
                .moderation(moderation)
                .n(n)
                .outputCompression(outputCompression)
                .outputFormat(outputFormat)
                .quality(quality)
                .responseFormat(responseFormat)
                .size(size)
                .style(style)
                .user(user)
                .build();
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ImageModel imageModel = WatsonxGatewayImageModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .modelName("gpt-image-1")
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxGatewayImageModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxConnectionBuilder<Builder> {
        private String modelName;
        private String background;
        private String moderation;
        private Integer outputCompression;
        private String outputFormat;
        private String quality;
        private String responseFormat;
        private String size;
        private String style;
        private String user;

        private Builder() {}

        /**
         * Sets the gateway image model id, e.g. {@code "gpt-image-1"}.
         *
         * @param modelName the model id
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the background of the generated images. Transparency requires an output format that supports it, so
         * {@link OutputFormat#PNG} or {@link OutputFormat#WEBP}.
         *
         * @param background the {@link Background}
         * @return {@code this}
         */
        public Builder background(Background background) {
            this.background = isNull(background) ? null : background.value();
            return this;
        }

        /**
         * Sets the background of the generated images as a raw string, for the values that {@link Background} does not
         * cover yet.
         *
         * @param background the background
         * @return {@code this}
         */
        public Builder background(String background) {
            this.background = background;
            return this;
        }

        /**
         * Sets how strictly the generated images are filtered, {@link Moderation#LOW} being the least restrictive.
         *
         * @param moderation the {@link Moderation}
         * @return {@code this}
         */
        public Builder moderation(Moderation moderation) {
            this.moderation = isNull(moderation) ? null : moderation.value();
            return this;
        }

        /**
         * Sets how strictly the generated images are filtered as a raw string, for the values that {@link Moderation}
         * does not cover yet.
         *
         * @param moderation the moderation level
         * @return {@code this}
         */
        public Builder moderation(String moderation) {
            this.moderation = moderation;
            return this;
        }

        /**
         * Sets the compression level of the generated images, from 0 to 100. Only the {@link OutputFormat#JPEG} and
         * {@link OutputFormat#WEBP} formats accept it.
         *
         * @param outputCompression the compression level
         * @return {@code this}
         */
        public Builder outputCompression(Integer outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        /**
         * Sets the file format of the generated images.
         *
         * @param outputFormat the {@link OutputFormat}
         * @return {@code this}
         */
        public Builder outputFormat(OutputFormat outputFormat) {
            this.outputFormat = isNull(outputFormat) ? null : outputFormat.value();
            return this;
        }

        /**
         * Sets the file format of the generated images as a raw string, for the formats that {@link OutputFormat} does
         * not cover yet.
         *
         * @param outputFormat the output format
         * @return {@code this}
         */
        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * Sets the quality of the generated images.
         *
         * @param quality the {@link Quality}
         * @return {@code this}
         */
        public Builder quality(Quality quality) {
            this.quality = isNull(quality) ? null : quality.value();
            return this;
        }

        /**
         * Sets the quality of the generated images as a raw string, for the values that {@link Quality} does not cover
         * yet.
         *
         * @param quality the image quality
         * @return {@code this}
         */
        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        /**
         * Sets how the generated images are returned, as a link with {@link ResponseFormat#URL} or as Base64 data with
         * {@link ResponseFormat#B64_JSON}. Only the models that support it accept this value.
         *
         * @param responseFormat the {@link ResponseFormat}
         * @return {@code this}
         */
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = isNull(responseFormat) ? null : responseFormat.value();
            return this;
        }

        /**
         * Sets how the generated images are returned as a raw string, for the formats that {@link ResponseFormat} does
         * not cover yet.
         *
         * @param responseFormat the response format
         * @return {@code this}
         */
        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Sets the dimensions of the generated images.
         *
         * @param size the {@link Size}
         * @return {@code this}
         */
        public Builder size(Size size) {
            this.size = isNull(size) ? null : size.value();
            return this;
        }

        /**
         * Sets the dimensions of the generated images as a raw string, e.g. {@code "1024x1024"}.
         *
         * @param size the image size
         * @return {@code this}
         */
        public Builder size(String size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the visual style of the generated images.
         *
         * @param style the {@link Style}
         * @return {@code this}
         */
        public Builder style(Style style) {
            this.style = isNull(style) ? null : style.value();
            return this;
        }

        /**
         * Sets the visual style of the generated images as a raw string, for the values that {@link Style} does not
         * cover yet.
         *
         * @param style the image style
         * @return {@code this}
         */
        public Builder style(String style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the identifier of the end user, used for abuse monitoring.
         *
         * @param user the user identifier
         * @return {@code this}
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public WatsonxGatewayImageModel build() {
            return new WatsonxGatewayImageModel(this);
        }
    }
}
