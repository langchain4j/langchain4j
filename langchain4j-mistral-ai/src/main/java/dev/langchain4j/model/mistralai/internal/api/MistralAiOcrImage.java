package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * An image extracted from a page, located by its bounding box. {@link #imageBase64} is only populated
 * when the request asked for the image contents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiOcrImage {

    public String id;
    public Integer topLeftX;
    public Integer topLeftY;
    public Integer bottomRightX;
    public Integer bottomRightY;
    public String imageBase64;
}
