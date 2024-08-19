package dev.langchain4j.model.bedrock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class BedrockAnthropicImageSource {
  
  private String type;
  private String media_type;
  private String data;
}
