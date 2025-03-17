package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Retrieval {
    private final String knowledgeId;
    private final String promptTemplate;

    public Retrieval(String knowledgeId, String promptTemplate) {
        this.knowledgeId = knowledgeId;
        this.promptTemplate = promptTemplate;
    }

    public static RetrievalBuilder builder() {
        return new RetrievalBuilder();
    }

    public static class RetrievalBuilder {
        private String knowledgeId;
        private String promptTemplate;

        RetrievalBuilder() {
        }

        public RetrievalBuilder knowledgeId(String knowledgeId) {
            this.knowledgeId = knowledgeId;
            return this;
        }

        public RetrievalBuilder promptTemplate(String promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Retrieval build() {
            return new Retrieval(this.knowledgeId, this.promptTemplate);
        }

    }
}
