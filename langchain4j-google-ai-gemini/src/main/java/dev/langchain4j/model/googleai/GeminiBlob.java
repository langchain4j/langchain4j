package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiBlob {
    private String mimeType;
    private String data;
}
