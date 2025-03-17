package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Setter
@Builder
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
}
