package dev.langchain4j.store.embedding.infinispan;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class LangChainInfinispanItem {

    private final String id;
    private final float[] embedding;
    private final String text;
    private final List<String> metadataKeys;
    private final List<String> metadataValues;

    public LangChainInfinispanItem(String id,
                                   float[] embedding,
                                   String text,
                                   List<String> metadataKeys,
                                   List<String> metadataValues) {
        this.id = id;
        this.embedding = embedding;
        this.text = text;
        this.metadataKeys = metadataKeys;
        this.metadataValues = metadataValues;
    }

    /**
     * the id of the embedding
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Vector
     *
     * @return the vector
     */
    public float[] getEmbedding() {
        return embedding;
    }

    /**
     * Maps to the text segment text
     *
     * @return text
     */
    public String getText() {
        return text;
    }

    /**
     * Maps to the text segment metadata keys
     *
     * @return metadata keys
     */
    public List<String> getMetadataKeys() {
        return metadataKeys;
    }

    /**
     * Maps to the text segment metadata values
     *
     * @return metadata values
     */
    public List<String> getMetadataValues() {
        return metadataValues;
    }

    @Override
    public String toString() {
        return "LangchainInfinispanItem{" + "id='" + id + '\'' + ", embedding=" + Arrays.toString(embedding)
                + ", text='" + text + '\'' + ", metadataKeys=" + metadataKeys + ", metadataValues=" + metadataValues + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LangChainInfinispanItem that = (LangChainInfinispanItem) o;
        return Objects.equals(id, that.id) && Arrays.equals(embedding, that.embedding) && Objects.equals(text,
                that.text) && Objects.equals(metadataKeys, that.metadataKeys) && Objects.equals(metadataValues,
                that.metadataValues);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, text, metadataKeys, metadataValues);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }
}

