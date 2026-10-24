package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * The result of an OCR request, with one entry in {@link #pages} per processed page.
 *
 * <p>Fields not declared here are ignored, so newer model versions returning additional data do not
 * break the deserialization.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiOcrResponse {

    public List<MistralAiOcrPage> pages;
    public String model;
    public MistralAiOcrUsageInfo usageInfo;
}
