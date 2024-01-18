package dev.langchain4j.store.embedding.vearch.api;

import java.util.Map;

public class InsertionRequest {

    private String dbName;
    private String spaceName;
    private Map<String, Object> documents;

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

    public Map<String, Object> getDocuments() {
        return documents;
    }

    public void setDocuments(Map<String, Object> documents) {
        this.documents = documents;
    }
}
