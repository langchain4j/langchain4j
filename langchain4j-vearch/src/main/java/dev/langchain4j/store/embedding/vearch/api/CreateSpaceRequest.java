package dev.langchain4j.store.embedding.vearch.api;

import dev.langchain4j.store.embedding.vearch.api.space.ModelParam;
import dev.langchain4j.store.embedding.vearch.api.space.SpaceEngine;
import dev.langchain4j.store.embedding.vearch.api.space.SpacePropertyParam;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class CreateSpaceRequest {

    private String name;
    private Integer partitionNum;
    private Integer replicaNum;
    private SpaceEngine engine;
    private Map<String, SpacePropertyParam> properties;
    private List<ModelParam> models;
}
