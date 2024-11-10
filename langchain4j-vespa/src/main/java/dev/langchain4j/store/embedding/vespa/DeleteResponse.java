package dev.langchain4j.store.embedding.vespa;

class DeleteResponse {

    private String pathId;
    private Long documentCount;

    public DeleteResponse(String pathId, Long documentCount) {
        this.pathId = pathId;
        this.documentCount = documentCount;
    }

    public DeleteResponse() {}

    public String getPathId() {
        return this.pathId;
    }

    public Long getDocumentCount() {
        return this.documentCount;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public void setDocumentCount(Long documentCount) {
        this.documentCount = documentCount;
    }
}
