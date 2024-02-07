package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@ToString
@EqualsAndHashCode
public class Not implements MetadataFilter {

    private final MetadataFilter expression;

    public Not(MetadataFilter expression) {
        this.expression = ensureNotNull(expression, "expression");
    }

    public MetadataFilter expression() {
        return expression;
    }

    @Override
    public boolean test(Metadata metadata) {
        return !expression.test(metadata);
    }
}
