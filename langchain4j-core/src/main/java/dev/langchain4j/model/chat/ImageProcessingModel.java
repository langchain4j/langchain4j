package dev.langchain4j.model.chat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;

public interface ImageProcessingModel {
    Response<Image> generate(String prompt);
}
