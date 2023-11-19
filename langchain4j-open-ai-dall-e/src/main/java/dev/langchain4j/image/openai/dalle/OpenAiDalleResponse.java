package dev.langchain4j.image.openai.dalle;

import java.util.List;
import lombok.Data;

@Data
class OpenAiDalleResponse {

  private long created;
  private List<ImageData> data;

  @Data
  static class ImageData {

    private String url;
  }
}
