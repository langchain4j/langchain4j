package dev.langchain4j.image.openai.dalle;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class OpenAiDalleRequest {

    private String model;
    private String prompt;
    @Builder.Default
    private int n = 1;
    private String size;
    private String quality;
    private String style;
}
