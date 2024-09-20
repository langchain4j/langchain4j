package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class CreateSpaceRequest {

    private String name;
    private Integer partitionNum;
    private Integer replicaNum;
    private SpaceEngine engine;
    private Map<String, SpacePropertyParam> properties;
    private List<ModelParam> models;

    CreateSpaceRequest() {
    }

    CreateSpaceRequest(String name, Integer partitionNum, Integer replicaNum, SpaceEngine engine, Map<String, SpacePropertyParam> properties, List<ModelParam> models) {
        this.name = name;
        this.partitionNum = partitionNum;
        this.replicaNum = replicaNum;
        this.engine = engine;
        this.properties = properties;
        this.models = models;
    }

    public String getName() {
        return name;
    }

    public Integer getPartitionNum() {
        return partitionNum;
    }

    public Integer getReplicaNum() {
        return replicaNum;
    }

    public SpaceEngine getEngine() {
        return engine;
    }

    public Map<String, SpacePropertyParam> getProperties() {
        return properties;
    }

    public List<ModelParam> getModels() {
        return models;
    }

    static class Builder {

        private String name;
        private Integer partitionNum;
        private Integer replicaNum;
        private SpaceEngine engine;
        private Map<String, SpacePropertyParam> properties;
        private List<ModelParam> models;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder partitionNum(Integer partitionNum) {
            this.partitionNum = partitionNum;
            return this;
        }

        Builder replicaNum(Integer replicaNum) {
            this.replicaNum = replicaNum;
            return this;
        }

        Builder engine(SpaceEngine engine) {
            this.engine = engine;
            return this;
        }

        Builder properties(Map<String, SpacePropertyParam> properties) {
            this.properties = properties;
            return this;
        }

        Builder models(List<ModelParam> models) {
            this.models = models;
            return this;
        }

        CreateSpaceRequest build() {
            return new CreateSpaceRequest(name, partitionNum, replicaNum, engine, properties, models);
        }
    }
}
