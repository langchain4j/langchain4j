package dev.langchain4j.model.workersai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import dev.langchain4j.model.workersai.client.WorkersAiImageGenerationRequest;
import dev.langchain4j.model.workersai.spi.WorkersAiImageModelBuilderFactory;
import okhttp3.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * WorkerAI Image model.
 */
public class WorkersAiImageModel extends AbstractWorkersAIModel implements ImageModel {

    /**
     * The mime type returned by Workers
     */
    private static final String MIME_TYPE = "image/png";

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkersAiImageModel(Builder builder) {
        this(builder.accountId, builder.modelName, builder.apiToken);
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
        try {
            // Mapping inbound
            WorkersAiImageGenerationRequest imgReq = new WorkersAiImageGenerationRequest();
            imgReq.setPrompt(prompt);
            if (image != null) {
                if (image.url() != null) {
                    imgReq.setImage(getPixels(image.url().toURL()));
                }
            }
            if (mask != null) {
                if (mask.url() != null) {
                    imgReq.setMask(getPixels(mask.url().toURL()));
                }
            }

            retrofit2.Response<ResponseBody> response = workerAiClient
                    .generateImage(imgReq, accountId, modelName)
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                InputStream inputStream = response.body().byteStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return buffer.toByteArray();
            }
            throw new IllegalStateException("An error occured while generating image.");
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


