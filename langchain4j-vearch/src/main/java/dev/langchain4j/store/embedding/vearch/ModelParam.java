package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ModelParam {

    private String modelId;
    private List<String> fields;
    private String out;
}
