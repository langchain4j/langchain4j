package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiFileData {
    private String mimeType;
    private String fileUri;
}
