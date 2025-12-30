package dev.langchain4j.store.embedding.oceanbase;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;

class CollectionRequestBuilder {

    static String buildQueryExpression(List<String> ids, String idFieldName) {
        return ids.stream()
                .map(id -> format("%s = '%s'", idFieldName, id))
                .collect(joining(" OR "));
    }

    static String buildWhereExpression(Filter filter, FieldDefinition fieldDefinition) {
        if (filter == null) {
            return null;
        }
        return OceanBaseMetadataFilterMapper.map(filter, fieldDefinition);
    }

    static String buildDeleteExpression(List<String> ids, String idFieldName) {
        return buildQueryExpression(ids, idFieldName);
    }

    static String buildDeleteAllExpression(String idFieldName) {
        return format("%s != ''", idFieldName);
    }
}

