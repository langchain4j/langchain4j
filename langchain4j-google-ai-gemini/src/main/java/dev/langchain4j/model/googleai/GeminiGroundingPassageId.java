package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiGroundingPassageId {
    private String passageId;
    private String partIndex;
}
