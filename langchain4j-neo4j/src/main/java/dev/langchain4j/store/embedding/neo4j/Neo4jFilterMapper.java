package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.Getter;

import java.util.AbstractMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Neo4jFilterMapper {

    public static class IncrementalKeyMap {
        @Getter
        private final Map<String, Object> map = new ConcurrentHashMap<>();
        private int counter = 1;

        public String put(Object value) {
            String key = "param_" + counter++;
            map.put(key, value);
            return key;
        }
    }


    public Neo4jFilterMapper() {}

    final IncrementalKeyMap map = new IncrementalKeyMap();

    AbstractMap.SimpleEntry<String, Map> map(Filter filter) {
        final String stringMapPair = getStringMapping(filter);
        return new AbstractMap.SimpleEntry<>(stringMapPair, map.getMap());
    }

    private String getStringMapping(Filter filter) {
        if (filter instanceof IsEqualTo item) {
            return getOperation(item.key(), "=", item.comparisonValue());
        } else if (filter instanceof IsNotEqualTo item) {
            return getOperation(item.key(), "<>", item.comparisonValue());
        } else if (filter instanceof IsGreaterThan item) {
            return getOperation(item.key(), ">", item.comparisonValue());
        } else if (filter instanceof IsGreaterThanOrEqualTo item) {
            return getOperation(item.key(), ">=", item.comparisonValue());
        } else if (filter instanceof IsLessThan item) {
            return getOperation(item.key(), "<", item.comparisonValue());
        } else if (filter instanceof IsLessThanOrEqualTo item) {
            return getOperation(item.key(), "<=", item.comparisonValue());
        } else if (filter instanceof IsIn item) {
            return mapIn(item);
        } else if (filter instanceof IsNotIn item) {
            return mapNotIn(item);
        } else if (filter instanceof And item) {
            return mapAnd(item);
        } else if (filter instanceof Not item) {
            return mapNot(item);
        } else if (filter instanceof Or item) {
            return mapOr(item);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String getOperation(String key, String operator, Object value) {
        // put ($param_N, <value>) entry map
        final String param = map.put(value);

        return "n.`%s` %s $%s".formatted(
                key, operator, param
        );
    }

    public String mapIn(IsIn filter) {
        return getOperation(filter.key(), "IN", filter.comparisonValues());
    }

    public String mapNotIn(IsNotIn filter) {
        final String inOperation = getOperation(filter.key(), "IN", filter.comparisonValues());
        return "NOT (%s)".formatted(inOperation);
    }

    private String mapAnd(And filter) {
        return "(%s) AND (%s)".formatted(
                getStringMapping(filter.left()), getStringMapping(filter.right())
        );
    }

    private String mapOr(Or filter) {
        return "(%s) OR (%s)".formatted(
                getStringMapping(filter.left()), getStringMapping(filter.right())
        );
    }

    private String mapNot(Not filter) {
        return "NOT (%s)".formatted(
                getStringMapping(filter.expression())
        );
    }
}
