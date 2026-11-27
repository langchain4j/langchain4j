package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * A single page of an OCR result. {@link #markdown} holds the recognized content, {@link #index} the
 * zero-based position of the page in the source document.
 *
 * <p>{@link #header} and {@link #footer} are only populated when the request asked for them to be
 * extracted, and in that case their text is no longer part of {@link #markdown}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiOcrPage {

    public Integer index;
    public String markdown;
    public String header;
    public String footer;
    public List<MistralAiOcrImage> images;
    public MistralAiOcrDimensions dimensions;
}
