package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiBlob {
    private String mimeType;
    private String data;
}
