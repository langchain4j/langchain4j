package dev.langchain4j.model.info;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents an AI model provider with its configuration and available models.
 */
public class Provider {
    private String id;
    private List<String> env;
    private String npm;
    private String api;
    private String name;
    private String doc;
    private Map<String, ModelInfo> models;

    public Provider() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getEnv() {
        return env;
    }

    public void setEnv(List<String> env) {
        this.env = env;
    }

    public String getNpm() {
        return npm;
    }

    public void setNpm(String npm) {
        this.npm = npm;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public Map<String, ModelInfo> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelInfo> models) {
        this.models = models;
    }

    // Utility methods
    public ModelInfo getModel(String modelId) {
        return models != null ? models.get(modelId) : null;
    }

    public List<ModelInfo> getAllModels() {
        return models != null ? models.values().stream().collect(Collectors.toList()) : List.of();
    }

    public List<ModelInfo> getModelsByFamily(String family) {
        if (models == null) {
            return List.of();
        }
        return models.values().stream()
                .filter(m -> family.equals(m.getFamily()))
                .collect(Collectors.toList());
    }

    public List<ModelInfo> getFreeModels() {
        if (models == null) {
            return List.of();
        }
        return models.values().stream().filter(ModelInfo::isFree).collect(Collectors.toList());
    }

    public List<ModelInfo> getReasoningModels() {
        if (models == null) {
            return List.of();
        }
        return models.values().stream().filter(ModelInfo::supportsReasoning).collect(Collectors.toList());
    }

    public List<ModelInfo> getMultimodalModels() {
        if (models == null) {
            return List.of();
        }
        return models.values().stream().filter(ModelInfo::isMultimodal).collect(Collectors.toList());
    }

    public int getModelCount() {
        return models != null ? models.size() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Provider provider = (Provider) o;
        return Objects.equals(id, provider.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Provider{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", api='" + api + '\'' + ", modelCount="
                + getModelCount() + '}';
    }
}
