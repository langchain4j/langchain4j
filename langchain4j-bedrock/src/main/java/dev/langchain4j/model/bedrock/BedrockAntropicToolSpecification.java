package dev.langchain4j.model.bedrock;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedrockAntropicToolSpecification {

    private String name;
    private String description;
    private Object input_schema;

}
