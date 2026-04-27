package dev.langchain4j.model.image;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
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
     * Edit one or more existing images following the given prompt and return {@code n} variations.
     *
     * <p>This is the canonical edit method. The other {@code edit(...)} overloads on this
     * interface are convenience defaults that delegate here with appropriate defaults
     * ({@code mask=null} where omitted, {@code n=1} where omitted), so an implementation
     * only needs to override this one to support every overload.
     *
     * @param images The images to be edited.
     * @param mask   Optional mask delimiting the edit region; pass {@code null} to edit the
     *               whole image.
     * @param prompt The prompt describing the edit.
     * @param n      The number of edited images to return.
     * @return The generated images Response.
     * @throws IllegalArgumentException if the operation is not supported.
     */
    default Response<List<Image>> edit(List<Image> images, Image mask, String prompt, int n) {
        throw new IllegalArgumentException("Operation is not supported");
    }

    /**
     * Convenience overload — equivalent to {@code edit(Collections.singletonList(image), null, prompt, 1)}.
     */
    default Response<Image> edit(Image image, String prompt) {
        return single(edit(Collections.singletonList(image), null, prompt, 1));
    }

    /**
     * Convenience overload — equivalent to {@code edit(Collections.singletonList(image), mask, prompt, 1)}.
     */
    default Response<Image> edit(Image image, Image mask, String prompt) {
        return single(edit(Collections.singletonList(image), mask, prompt, 1));
    }

    /**
     * Convenience overload — equivalent to {@code edit(images, null, prompt, 1)}.
     *
     * <p>For multi-image edit, callers should use a provider that accepts multiple input
     * images per edit request (e.g. OpenAI's gpt-image-* models).
     */
    default Response<Image> edit(List<Image> images, String prompt) {
        return single(edit(images, null, prompt, 1));
    }

    /**
     * Convenience overload — equivalent to {@code edit(images, null, prompt, n)}.
     */
    default Response<List<Image>> edit(List<Image> images, String prompt, int n) {
        return edit(images, null, prompt, n);
    }

    /**
     * Convenience overload — equivalent to {@code edit(images, mask, prompt, 1)}.
     */
    default Response<Image> edit(List<Image> images, Image mask, String prompt) {
        return single(edit(images, mask, prompt, 1));
    }

    private static Response<Image> single(Response<List<Image>> response) {
        Image first = response.content().get(0);
        return Response.from(first, response.tokenUsage(), response.finishReason());
    }
}
