package dev.langchain4j.agentic;

import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonInMemoryAgenticScopeStore implements AgenticScopeStore {

    private final Map<AgenticScopeKey, String> jsonAgenticScopes = new HashMap<>();
    private final List<Object> loadedIds = new ArrayList<>();

    @Override
    public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
        jsonAgenticScopes.put(key, AgenticScopeSerializer.toJson(agenticScope));
        return true;
    }

    @Override
    public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
        return Optional.ofNullable(jsonAgenticScopes.get(key))
                .map(s -> {
                    loadedIds.add(key.memoryId());
                    return AgenticScopeSerializer.fromJson(s);
                });
    }

    @Override
    public boolean delete(AgenticScopeKey key) {
        return jsonAgenticScopes.remove(key) != null;
    }

    @Override
    public Set<AgenticScopeKey> getAllKeys() {
        return jsonAgenticScopes.keySet();
    }

    public String getJson(Object id) {
        return jsonAgenticScopes.get(id);
    }

    public List<Object> getLoadedIds() {
        return loadedIds;
    }
}
