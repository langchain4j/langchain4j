package dev.langchain4j.data.document;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Metadata {

    private final Map<String, String> metadata;

    public Metadata() {
        this(new HashMap<>());
    }

    public Metadata(Map<String, String> metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        this.metadata = metadata;
    }

    public String get(String key) {
        return metadata.get(key);
    }

    public Metadata add(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    public void mergeFrom(Metadata other) {
        this.metadata.putAll(other.metadata);
    }

    public Metadata copy() {
        return new Metadata(new HashMap<>(metadata));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metadata that = (Metadata) o;
        return Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadata);
    }

    @Override
    public String toString() {
        return "Metadata {" +
                " metadata = " + metadata +
                " }";
    }
}
