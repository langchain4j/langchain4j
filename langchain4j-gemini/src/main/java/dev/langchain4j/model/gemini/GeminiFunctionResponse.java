package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class GeminiFunctionResponse {
    private String name;
    private Map response;
}
