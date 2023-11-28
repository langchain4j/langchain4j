package dev.langchain4j.model.image;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;

public interface ImageModel {
  Response<Image> generate(String prompt);
}
