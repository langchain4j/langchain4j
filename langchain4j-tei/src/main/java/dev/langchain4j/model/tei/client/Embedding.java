package dev.langchain4j.model.tei.client;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class Embedding {

    private List<Float> embedding;

    private Integer index;
}
