package dev.langchain4j.model.bedrock;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
@Getter
@Setter
@AllArgsConstructor
public class BedrockAnthropicMessage {
  
  private String role;
  private List<BedrockAnthropicContent> content;
}
