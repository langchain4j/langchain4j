package dev.langchain4j.model.mistralai;

import dev.langchain4j.agent.tool.ToolParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiParameters {

    @Builder.Default
    private String type="object";
    private Map<String,Map<String,Object>> properties;
    private List<String> required;

    static MistralAiParameters from(ToolParameters toolParameters){
        return MistralAiParameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }
}
