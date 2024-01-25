package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.store.embedding.vearch.ModelParam;
import dev.langchain4j.store.embedding.vearch.SpaceEngine;
import dev.langchain4j.store.embedding.vearch.SpacePropertyParam;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
class CreateSpaceRequest {

    private String name;
    private Integer partitionNum;
    private Integer replicaNum;
    private SpaceEngine engine;
    private Map<String, SpacePropertyParam> properties;
    private List<ModelParam> models;
}
