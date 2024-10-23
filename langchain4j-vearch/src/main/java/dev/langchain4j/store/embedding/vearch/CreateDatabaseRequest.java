package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class CreateDatabaseRequest {

    private String name;

    CreateDatabaseRequest() {
    }

    CreateDatabaseRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String name;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        CreateDatabaseRequest build() {
            return new CreateDatabaseRequest(name);
        }
    }
}
