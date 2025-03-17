package dev.langchain4j.model.bedrock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BedrockAntropicToolSpecification {

    private String name;
    private String description;
    private Object input_schema;

}
