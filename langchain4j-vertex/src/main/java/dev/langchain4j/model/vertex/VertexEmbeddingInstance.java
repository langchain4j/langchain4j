package dev.langchain4j.model.vertex;

class VertexEmbeddingInstance {

    private final String content;

    public VertexEmbeddingInstance(String content) {
        this.content = content;
    }

    public String content() {
        return content;
    }
}
