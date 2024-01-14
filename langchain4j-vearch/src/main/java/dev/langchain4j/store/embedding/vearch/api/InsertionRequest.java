package dev.langchain4j.store.embedding.vearch.api;

import dev.langchain4j.store.embedding.vearch.Document;

import java.util.List;

public class InsertionRequest {

    private String dbName;
    private String spaceName;
    private List<Document> documents;

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }
}
