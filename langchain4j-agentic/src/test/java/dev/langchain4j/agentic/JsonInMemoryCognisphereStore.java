package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.CognisphereKey;
import dev.langchain4j.agentic.cognisphere.CognisphereStore;
import dev.langchain4j.agentic.cognisphere.CognisphereSerializer;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonInMemoryCognisphereStore implements CognisphereStore {

    private final Map<CognisphereKey, String> jsonCognispheres = new HashMap<>();
    private final List<Object> loadedIds = new ArrayList<>();

    @Override
    public boolean save(DefaultCognisphere cognisphere) {
        jsonCognispheres.put(cognisphere.key(), CognisphereSerializer.toJson((DefaultCognisphere) cognisphere));
        return true;
    }

    @Override
    public Optional<DefaultCognisphere> load(CognisphereKey key) {
        return Optional.ofNullable(jsonCognispheres.get(key))
                .map(s -> {
                    loadedIds.add(key.memoryId());
                    return CognisphereSerializer.fromJson(s);
                });
    }

    @Override
    public boolean delete(CognisphereKey key) {
        return jsonCognispheres.remove(key) != null;
    }

    @Override
    public Set<CognisphereKey> getAllKeys() {
        return jsonCognispheres.keySet();
    }

    public String getJson(Object id) {
        return jsonCognispheres.get(id);
    }

    public List<Object> getLoadedIds() {
        return loadedIds;
    }
}
