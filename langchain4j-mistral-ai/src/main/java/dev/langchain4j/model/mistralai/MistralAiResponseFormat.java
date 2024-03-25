package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiResponseFormat {

    private Object type;

    static MistralAiResponseFormat fromType(MistralAiResponseFormatType type) {
        return MistralAiResponseFormat.builder()
                .type(type.toString())
                .build();
    }
}
