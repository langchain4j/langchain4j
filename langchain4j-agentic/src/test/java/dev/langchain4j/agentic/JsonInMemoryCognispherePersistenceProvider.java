package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.CognispherePersistenceProvider;
import dev.langchain4j.agentic.cognisphere.CognisphereSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonInMemoryCognispherePersistenceProvider implements CognispherePersistenceProvider {

    private final Map<Object, String> jsonCognispheres = new HashMap<>();
    private final List<Object> loadedIds = new ArrayList<>();

    @Override
    public boolean save(Cognisphere cognisphere) {
        jsonCognispheres.put(cognisphere.id(), CognisphereSerializer.toJson(cognisphere));
        return true;
    }

    @Override
    public Optional<Cognisphere> load(Object id) {
        return Optional.ofNullable(jsonCognispheres.get(id))
                .map(s -> {
                    loadedIds.add(id);
                    return CognisphereSerializer.fromJson(s);
                });
    }

    @Override
    public boolean delete(Object id) {
        return jsonCognispheres.remove(id) != null;
    }

    @Override
    public Set<Object> getAllIds() {
        return jsonCognispheres.keySet();
    }

    public String getJson(Object id) {
        return jsonCognispheres.get(id);
    }

    public List<Object> getLoadedIds() {
        return loadedIds;
    }
}
