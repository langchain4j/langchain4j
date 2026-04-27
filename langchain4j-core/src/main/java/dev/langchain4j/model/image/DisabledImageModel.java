package dev.langchain4j.model.image;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * An {@link ImageModel} which throws a {@link ModelDisabledException} for all of its methods
 * <p>
 *     This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledImageModel implements ImageModel {
    @Override
    public Response<Image> generate(String prompt) {
        throw new ModelDisabledException("ImageModel is disabled");
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        throw new ModelDisabledException("ImageModel is disabled");
    }

    /**
     * Overriding the canonical edit method is enough — the other edit overloads inherit
     * delegating defaults from {@link ImageModel}, which call this one and so propagate the
     * same {@link ModelDisabledException}.
     */
    @Override
    public Response<List<Image>> edit(List<Image> images, Image mask, String prompt, int n) {
        throw new ModelDisabledException("ImageModel is disabled");
    }
}
