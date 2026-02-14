package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.jspecify.annotations.NonNull;

/**
 * A batch model for processing multiple image generation requests asynchronously.
 */
@Experimental
public interface BatchImageModel extends BatchModel<String, Response<Image>> {}
