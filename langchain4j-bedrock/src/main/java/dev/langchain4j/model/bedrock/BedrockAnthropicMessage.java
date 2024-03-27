package dev.langchain4j.model.bedrock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class BedrockAnthropicMessage {
  
  private String role;
  private List<BedrockAnthropicContent> content;
}
