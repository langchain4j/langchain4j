package dev.langchain4j.model.bedrock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BedrockAnthropicContent {
  
  private String type;
  private String text;
  private BedrockAnthropicImageSource source;
  
  public BedrockAnthropicContent(String type, String text) {
    this.type = type;
    this.text = text;
  }
  
  public BedrockAnthropicContent(String type, BedrockAnthropicImageSource source) {
    this.type = type;
    this.source = source;
  }
}
