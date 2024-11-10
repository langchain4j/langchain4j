package dev.langchain4j.model.mistralai.internal.api;


import java.util.List;

public class MistralAiModerationResponse {

    private String id;
    private String model;
    private List<MistralModerationResult> results;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<MistralModerationResult> getResults() {
        return results;
    }

    public void setResults(List<MistralModerationResult> results) {
        this.results = results;
    }
}
