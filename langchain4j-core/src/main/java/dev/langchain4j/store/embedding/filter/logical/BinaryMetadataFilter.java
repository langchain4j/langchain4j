package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@ToString
@EqualsAndHashCode
public abstract class BinaryMetadataFilter implements MetadataFilter {

    private final MetadataFilter left;
    private final MetadataFilter right;

    protected BinaryMetadataFilter(MetadataFilter left, MetadataFilter right) {
        this.left = ensureNotNull(left, "left");
        this.right = ensureNotNull(right, "right");
    }

    public MetadataFilter left() {
        return left;
    }

    public MetadataFilter right() {
        return right;
    }
}