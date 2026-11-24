package dev.langchain4j.model.workersai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import dev.langchain4j.model.workersai.client.WorkersAiImageGenerationRequest;
import dev.langchain4j.model.workersai.spi.WorkersAiImageModelBuilderFactory;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * WorkerAI Image model.
 * <p>
 * To edit an image, give its content as base64 data, for example
 * {@code Image.builder().base64Data(base64Data).mimeType("image/png").build()}. Images given by URL are not supported.
 * Only PNG, JPEG, GIF, BMP and WebP images can be edited (WebP only if an image reader for it is installed), with at
 * most 2048 x 2048 pixels, a limit of LangChain4j.
 */
public class WorkersAiImageModel extends AbstractWorkersAIModel implements ImageModel {

    /**
     * The mime type returned by Workers
     */
    private static final String MIME_TYPE = "image/png";

    /**
     * The largest width times height of an image that this model decodes (2048 x 2048). This is a limit of LangChain4j,
     * not of Workers AI: decoding needs memory for every pixel, whatever the size of the file, so a larger image is
     * rejected before it is decoded.
     */
    private static final long MAX_PIXELS = 2048L * 2048L;

    /**
     * The image formats that this model decodes. Only the image readers of these formats are used, so that no other
     * image reader that happens to be installed reads the content.
     */
    private static final List<String> IMAGE_FORMATS = List.of("png", "jpeg", "gif", "bmp", "webp");

    static final String IMAGE_URL_NOT_SUPPORTED = "Workers AI does not support images given by URL; it needs the image"
            + " content. Create the image from the content of a file or from its bytes, for example"
            + " Image.builder().base64Data(base64Data).mimeType(mimeType).build()";

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkersAiImageModel(Builder builder) {
        super(builder.accountId, builder.modelName, builder.apiToken, builder.httpClientBuilder);
    }

    /**
     * Constructor with Builder.
     *
     * @param accountId
     *      account identifier
     * @param modelName
     *      model name
     * @param apiToken
     *     api token
     */
    public WorkersAiImageModel(String accountId, String modelName, String apiToken) {
        super(accountId, modelName, apiToken);
    }

    /**
     * Builder access.
     *
     * @return
     *      builder instance
     */
    public static Builder builder() {
        for (WorkersAiImageModelBuilderFactory factory : loadFactories(WorkersAiImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new WorkersAiImageModel.Builder();
    }

    /**
     * Internal Builder.
     */
    public static class Builder {

        /**
         * Account identifier, provided by the WorkerAI platform.
         */
        public String accountId;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String apiToken;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String modelName;
        /**
         * The HTTP client builder used to create the underlying HTTP client.
         */
        public HttpClientBuilder httpClientBuilder;

        /**
         * Simple constructor.
         */
        public Builder() {
        }

        /**
         * Simple constructor.
         *
         * @param accountId
         *      account identifier.
         * @return
         *      self reference
         */
        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        /**
         * Sets the apiToken for the Worker AI model builder.
         *
         * @param apiToken The apiToken to set.
         * @return The current instance of {@link WorkersAiChatModel.Builder}.
         */
        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        /**
         * Sets the model name for the Worker AI model builder.
         *
         * @param modelName The name of the model to set.
         * @return The current instance of {@link WorkersAiChatModel.Builder}.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the {@link HttpClientBuilder} used to create the underlying HTTP client.
         *
         * @param httpClientBuilder The HTTP client builder to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Builds a new instance of Worker AI Chat Model.
         *
         * @return A new instance of {@link WorkersAiChatModel}.
         */
        public WorkersAiImageModel build() {
            return new WorkersAiImageModel(this);
        }
    }

    /** {@inheritDoc} */
    public Response<Image> generate(String prompt) {
        ensureNotBlank(prompt, "Prompt");
        return new Response<>(convertAsImage(executeQuery(prompt, null, null)), null, FinishReason.STOP);
    }

    /** {@inheritDoc} */
    public Response<Image> edit(Image image, String prompt) {
        ensureNotBlank(prompt, "Prompt");
        ensureNotNull(image, "Image");
        return new Response<>(convertAsImage(executeQuery(prompt, null, image)), null, FinishReason.STOP);
    }

    /** {@inheritDoc} */
    public Response<Image> edit(Image image, Image mask, String prompt) {
        ensureNotBlank(prompt, "Prompt");
        ensureNotNull(image, "Image");
        ensureNotNull(mask, "Mask");
        return new Response<>(convertAsImage(executeQuery(prompt, mask, image)), null, FinishReason.STOP);
    }

    /**
     * Generate image and save to file.
     *
     * @param prompt
     *      current prompt
     * @param destinationFile
     *      local file
     * @return
     *      response with the destination file
     */
    public Response<File> generate(String prompt, String destinationFile) {
        ensureNotBlank(prompt, "Prompt");
        ensureNotBlank(destinationFile, "Destination file");
        try {
            byte[] image = executeQuery(prompt, null, null);
            try (FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                fileOutputStream.write(image);
            }
            return new Response<>(new File(destinationFile), null, FinishReason.STOP);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute query.
     *
     * @param prompt
     *     prompt.
     * @return
     *    image.
     */
    private byte[] executeQuery(String prompt, Image image, Image mask) {
        // Mapping inbound
        WorkersAiImageGenerationRequest imgReq = new WorkersAiImageGenerationRequest();
        imgReq.setPrompt(prompt);
        if (image != null) {
            imgReq.setImage(pixels(image));
        }
        if (mask != null) {
            imgReq.setMask(pixels(mask));
        }

        try {
            return client.generateImage(imgReq, accountId, modelName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert an image into a array of number, supposedly the Pixels.
     * @param imageUrl
     *      current image URL
     * @return
     *      pixels of the image
     * @throws Exception
     *      return an exception if pixel not returned
     */
    public int[] getPixels(URL imageUrl) throws Exception {
        BufferedImage image = ImageIO.read(imageUrl);
        return pixels(image);
    }

    private static int[] pixels(Image image) {
        if (image.base64Data() != null) {
            return pixels(Base64.getDecoder().decode(image.base64Data()));
        }
        if (image.url() == null) {
            throw new IllegalArgumentException("The image has neither base64 data nor a URL");
        }
        throw new UnsupportedFeatureException(IMAGE_URL_NOT_SUPPORTED);
    }

    private static int[] pixels(byte[] imageBytes) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(imageBytes))) {
            ImageReader reader = imageReader(input);
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new InvalidRequestException("The image has " + width + " x " + height
                            + " pixels, more than LangChain4j decodes for Workers AI (" + MAX_PIXELS
                            + " pixels, 2048 x 2048)");
                }
                return pixels(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidRequestException("The image cannot be read", e);
        }
    }

    /**
     * Returns a reader of one of the {@link #IMAGE_FORMATS} that can read the image.
     */
    private static ImageReader imageReader(ImageInputStream input) throws IOException {
        for (String format : IMAGE_FORMATS) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                if (canDecode(reader.getOriginatingProvider(), input)) {
                    return reader;
                }
                reader.dispose();
            }
        }
        throw new InvalidRequestException(
                "The image format is not supported: only PNG, JPEG, GIF, BMP and WebP images can be edited");
    }

    private static boolean canDecode(@Nullable ImageReaderSpi provider, ImageInputStream input) throws IOException {
        if (provider == null) {
            return false;
        }
        long start = input.getStreamPosition();
        try {
            return provider.canDecodeInput(input);
        } catch (IOException e) {
            return false; // content shorter than the signature of this format
        } finally {
            input.seek(start);
        }
    }

    private static int[] pixels(BufferedImage image) {
        // Get image dimensions
        int width = image.getWidth();
        int height = image.getHeight();

        // Initialize an array to hold the pixel data
        int[] pixelData = new int[width * height];

        // Extract pixel data
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get pixel color at (x, y)
                int pixel = image.getRGB(x, y);

                // Extract the individual color components
                int alpha = (pixel >> 24) & 0xff;
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;

                // Combine the color components into a single integer
                int color = (alpha << 24) | (red << 16) | (green << 8) | blue;

                // Store the color in the array
                pixelData[index++] = color;
            }
        }
        return pixelData;
    }

    /**
     * Convert Workers AI Image Generation output to LangChain4j model.
     *
     * @param data
     *      output image
     * @return
     *      output image converted
     */
    public Image convertAsImage(byte[] data) {
        return Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(data))
                .mimeType(MIME_TYPE)
                .build();
    }

}


