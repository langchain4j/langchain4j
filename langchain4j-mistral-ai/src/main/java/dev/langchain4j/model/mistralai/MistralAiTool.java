package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiTool {

    private MistralAiToolType type;
    private MistralAiFunction function;

    static MistralAiTool from(MistralAiFunction function){
        return MistralAiTool.builder()
                .type(MistralAiToolType.FUNCTION)
                .function(function)
                .build();
    }
}
