package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;

public class And extends BinaryMetadataFilter {

    public And(MetadataFilter left, MetadataFilter right) {
        super(left, right);
    }

    @Override
    public boolean test(Metadata metadata) {
        return left().test(metadata) && right().test(metadata);
    }
}
