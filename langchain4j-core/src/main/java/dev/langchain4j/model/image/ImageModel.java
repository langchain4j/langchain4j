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

    /**
     * Given an existing image, edit this image following the given prompt.
     *
     * @param image  The image to be edited.
     * @param prompt The prompt to edit the image.
     * @return The generated image Response.
     */
    default Response<Image> edit(Image image, String prompt) {
        throw new IllegalArgumentException("Operation is not supported");
    }

    /**
     * Given an existing image, edit this image following the given prompt and
     * apply the changes only to the part of the image specified by the given mask.
     *
     * @param image  The image to be edited.
     * @param mask   The image mask to apply to delimit the area to edit.
     * @param prompt The prompt to edit the image.
     * @return The generated image Response.
     */
    default Response<Image> edit(Image image, Image mask, String prompt) {
        throw new IllegalArgumentException("Operation is not supported");
    }
}
