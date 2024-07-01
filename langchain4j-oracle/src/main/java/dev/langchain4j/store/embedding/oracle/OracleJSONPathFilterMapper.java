package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.stream.Collectors;

public class OracleJSONPathFilterMapper {
    /**
     * Generates a where clause using a JSON path expression generated from a Filter.
     * @param filter Filter to map to JSON path expression.
     * @return Where clause with appended JSON path expression.
     */
    public String whereClause(Filter filter) {
        final String jsonExistsClause = "where json_exists(metadata, '$?(%s)')";
        return String.format(jsonExistsClause, map(filter));
    }

    /**
     * Maps a Filter to a JSON path expression
     * @param filter Filter to map.
     * @return JSON path expression String.
     */
    private String map(Filter filter) {
        if (filter instanceof IsEqualTo) {
            IsEqualTo eq = (IsEqualTo) filter;
            return String.format("%s == %s", formatKey(eq.key()), formatValue(eq.comparisonValue()));
        } else if (filter instanceof IsNotEqualTo) {
            IsNotEqualTo ne = (IsNotEqualTo) filter;
            return String.format("%s != %s", formatKey(ne.key()), formatValue(ne.comparisonValue()));
        } else if (filter instanceof IsGreaterThan) {
            IsGreaterThan gt = (IsGreaterThan) filter;
            return String.format("%s > %s", formatKey(gt.key()), formatValue(gt.comparisonValue()));
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            IsGreaterThanOrEqualTo gte = (IsGreaterThanOrEqualTo) filter;
            return String.format("%s >= %s", formatKey(gte.key()), formatValue(gte.comparisonValue()));
        } else if (filter instanceof IsLessThan) {
            IsLessThan lt = (IsLessThan) filter;
            return String.format("%s < %s", formatKey(lt.key()), formatValue(lt.comparisonValue()));
        } else if (filter instanceof IsLessThanOrEqualTo) {
            IsLessThanOrEqualTo lte = (IsLessThanOrEqualTo) filter;
            return String.format("%s <= %s", formatKey(lte.key()), formatValue(lte.comparisonValue()));
        } else if (filter instanceof IsIn) {
            IsIn in = (IsIn) filter;
            return String.format("%s in %s", formatKey(in.key()), formatValues(in.comparisonValues()));
        } else if (filter instanceof IsNotIn) {
            IsNotIn ni = (IsNotIn) filter;
            return String.format("!(%s in %s)", formatKey(ni.key()), formatValues(ni.comparisonValues()));
        } else if (filter instanceof Not) {
            Not n = (Not) filter;
            return String.format("!(%s)", map(n.expression()));
        } else if (filter instanceof And) {
            And and = (And) filter;
            return String.format("%s && %s", map(and.left()), map(and.right()));
        } else if (filter instanceof Or) {
            Or or = (Or) filter;
            return String.format("(%s || %s)", map(or.left()), map(or.right()));
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String formatKey(String key) {
        return "@." + key;
    }

    private String formatValue(Object v) {
        if (v instanceof String) {
            return String.format("\"%s\"", v);
        } else {
            return v.toString();
        }
    }

    String formatValues(Collection<?> values) {
        return "(" + values.stream().map(this::formatValue)
                .collect(Collectors.joining(",")) + ")";
    }
}
