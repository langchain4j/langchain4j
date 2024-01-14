package dev.langchain4j.store.embedding.vearch.api;

import dev.langchain4j.store.embedding.vearch.api.space.SpaceEngine;
import dev.langchain4j.store.embedding.vearch.api.space.SpacePropertyParam;

import java.util.Map;

public class CreateSpaceRequest {

    private String name;
    private Integer partitionNum;
    private Integer replicaNum;
    private SpaceEngine engine;
    private Map<String, SpacePropertyParam> properties;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(Integer partitionNum) {
        this.partitionNum = partitionNum;
    }

    public Integer getReplicaNum() {
        return replicaNum;
    }

    public void setReplicaNum(Integer replicaNum) {
        this.replicaNum = replicaNum;
    }

    public SpaceEngine getEngine() {
        return engine;
    }

    public void setEngine(SpaceEngine engine) {
        this.engine = engine;
    }

    public Map<String, SpacePropertyParam> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, SpacePropertyParam> properties) {
        this.properties = properties;
    }
}
