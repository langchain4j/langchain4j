package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.field.Field;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class CreateSpaceRequest {

    private String name;
    private Integer partitionNum;
    private Integer replicaNum;
    private List<Field> fields;

    CreateSpaceRequest() {
    }

    CreateSpaceRequest(String name, Integer partitionNum, Integer replicaNum, List<Field> fields) {
        this.name = name;
        this.partitionNum = partitionNum;
        this.replicaNum = replicaNum;
        this.fields = fields;
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

    public List<Field> getFields() {
        return fields;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String name;
        private Integer partitionNum;
        private Integer replicaNum;
        private List<Field> fields;

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

        Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        CreateSpaceRequest build() {
            return new CreateSpaceRequest(name, partitionNum, replicaNum, fields);
        }
    }
}
