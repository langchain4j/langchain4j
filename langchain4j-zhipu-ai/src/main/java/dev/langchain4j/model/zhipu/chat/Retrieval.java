package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class Retrieval {
    @SerializedName("knowledge_id")
    private String knowledgeId;
    @SerializedName("prompt_template")
    private String promptTemplate;

    Retrieval(RetrievalBuilder builder) {
        this.knowledgeId = builder.knowledgeId;
        this.promptTemplate = builder.promptTemplate;
    }

    public static RetrievalBuilder builder() {
        return new RetrievalBuilder();
    }

    public String getKnowledgeId() {
        return this.knowledgeId;
    }

    public String getPromptTemplate() {
        return this.promptTemplate;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Retrieval
                && equalTo((Retrieval) another);
    }

    private boolean equalTo(Retrieval another) {
        return Objects.equals(knowledgeId, another.knowledgeId)
                && Objects.equals(promptTemplate, another.promptTemplate);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(knowledgeId);
        h += (h << 5) + Objects.hashCode(promptTemplate);
        return h;
    }

    public String toString() {
        return "Retrieval("
                + "knowledgeId=" + this.knowledgeId
                + ", promptTemplate=" + this.promptTemplate
                + ")";
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
