package dev.langchain4j.model.bedrock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
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
