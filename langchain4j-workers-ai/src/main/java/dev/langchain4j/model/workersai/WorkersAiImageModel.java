package dev.langchain4j.model.workersai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import dev.langchain4j.model.workersai.client.WorkersAiImageGenerationRequest;
import okhttp3.ResponseBody;

import javax.imageio.ImageIO;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

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
        super(builder);
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
                    System.out.println("Adding Image");
                }
            }
            if (mask != null) {
                if (mask.url() != null) {
                    imgReq.setMask(getPixels(mask.url().toURL()));
                    System.out.println("Adding mask");
                }
            }

            retrofit2.Response<ResponseBody> response = workerAiClient
                    .generateImage(imgReq, accountIdentifier, modelName)
                    .execute();

            System.out.println(response.code() + " " + response.message());
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
     * Convert Workers AI Image Generation output to Langchain4j model.
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


