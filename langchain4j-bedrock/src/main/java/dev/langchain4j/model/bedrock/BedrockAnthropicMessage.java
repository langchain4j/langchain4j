package dev.langchain4j.model.bedrock;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class BedrockAnthropicMessage {
  
  private String role;
  private List<BedrockAnthropicContent> content;
}
