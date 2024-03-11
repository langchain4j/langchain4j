package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class Retrieval {
    @SerializedName("knowledge_id")
    private final String knowledgeId;
    @SerializedName("prompt_template")
    private final String promptTemplate;

    Retrieval(RetrievalBuilder builder) {
        this.knowledgeId = builder.knowledgeId;
        this.promptTemplate = builder.promptTemplate;
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
            return new Retrieval(this);
        }
    }
}
