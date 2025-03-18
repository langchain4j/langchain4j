package dev.langchain4j.model.bedrock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedrockAnthropicContent {

  private String type;
  private String text;
  private String id;
  private String name;
  private String tool_use_id;
  private String content;
  private BedrockAnthropicImageSource source;
  private Map<String, Object> input;

  public BedrockAnthropicContent(String type, String text) {
    this.type = type;
    this.text = text;
  }

  public BedrockAnthropicContent(String type, BedrockAnthropicImageSource source) {
    this.type = type;
    this.source = source;
  }
}
