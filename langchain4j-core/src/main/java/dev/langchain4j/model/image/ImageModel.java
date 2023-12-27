package dev.langchain4j.model.image;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * Text to Image generator model.
 */
public interface ImageModel {
    /**
     * Given a prompt, generate an image.
     * @param prompt The prompt to generate an image from.
     * @return The generated image Response.
     */
    Response<Image> generate(String prompt);

    /**
     * Given a prompt, generate n images.
     *
     * <p>Not supported by all models; as explicit support is needed to generate <b>different</b>
     * images from the same prompt.
     *
     * @param prompt The prompt to generate images from.
     * @param n The number of images to generate.
     * @return The generated images Response.
     * @throws IllegalArgumentException if the operation is not supported.
     */
    default Response<List<Image>> generate(String prompt, int n) {
        throw new IllegalArgumentException("Operation is not supported");
    }
}
