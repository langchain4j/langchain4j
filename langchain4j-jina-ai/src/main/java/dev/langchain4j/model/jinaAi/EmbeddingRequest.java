package dev.langchain4j.model.jinaAi;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class EmbeddingRequest {
    String model;
    List<String> input;
}
