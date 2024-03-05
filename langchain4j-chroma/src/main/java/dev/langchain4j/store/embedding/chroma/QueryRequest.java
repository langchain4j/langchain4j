package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingWhere;
import dev.langchain4j.store.embedding.WhereOperator;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

class QueryRequest {

    private final List<List<Float>> queryEmbeddings;
    private final int nResults;
    private final List<String> include = asList("metadatas", "documents", "distances", "embeddings");

    private final Map<String, Object> where;

    public QueryRequest(List<Float> queryEmbedding, int nResults, EmbeddingWhere where) {
        this.queryEmbeddings = singletonList(queryEmbedding);
        this.nResults = nResults;
        this.where = decodeWhereFilter(where);
    }

    private Map<String, Object> decodeWhereFilter(EmbeddingWhere where) {
        if (Objects.isNull(where) || Objects.isNull(where.where())) {
            return null;
        }
        // TODO convert WhereOperator.IN and WhereOperator.NOT_IN to $or and $and->$nge
        List<EmbeddingWhere.EmbeddingWhereDict> dicts = where.where();
        if (dicts.size() > 0) {
            return recursionFn(dicts);
        }
        return null;
    }

    public Map<String, Object> getWhere() {
        return where;
    }

    /**
     * {"file-id": {"in": ["1", "2", "3"]} to {"and": [{"file-id": "1"}, {"file-id": "2"}, "file-id": "3"}]}
     */
    private Map<String, Object> recursionFn(List<EmbeddingWhere.EmbeddingWhereDict> list) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> resultArrayMap = new ArrayList<>();
        for (int index = list.size() - 1; index >= 0; index--) {
            EmbeddingWhere.EmbeddingWhereDict dict = list.get(index);
            // operator is "and" & "or"
            if (WhereOperator.AND.value().equals(dict.getOperator()) || WhereOperator.OR.value().equals(dict.getOperator())) {
                List<EmbeddingWhere.EmbeddingWhereDict> subList = (List<EmbeddingWhere.EmbeddingWhereDict>) (dict.getValue());
                Map<String, Object> result = recursionFn(subList);
                resultArrayMap.add(Utils.mapOf(convertOperator(dict.getOperator()), result));
                continue;
            }
            if (WhereOperator.IN.value().equals(dict.getOperator())) {
                List<Map<String, Object>> newValueArray = new ArrayList<>();
                ((List<Object>) dict.getValue()).stream().forEach(item -> newValueArray.add(Utils.mapOf(dict.getField(), item)));
                resultArrayMap.add(Utils.mapOf(convertOperator("and"), newValueArray));
            } else if (WhereOperator.NOT_IN.value().equals(dict.getOperator())) {
                List<Map<String, Object>> newValueArray = new ArrayList<>();
                ((List<Object>) dict.getValue()).stream()
                        .forEach(item -> newValueArray.add(Utils.mapOf(dict.getField(), Utils.mapOf(convertOperator(WhereOperator.NE.value()), item))));
                resultArrayMap.add(Utils.mapOf(convertOperator("and"), newValueArray));
            } else {
                resultArrayMap.add(Utils.mapOf(dict.getField(), Utils.mapOf(convertOperator(dict.getOperator()), dict.getValue())));
            }
        }
        if (resultArrayMap.size() > 1) {
            resultMap.put("$and", resultArrayMap);
        } else {
            resultMap.putAll(resultArrayMap.get(0));
        }
        return resultMap;
    }

    private String convertOperator(String key) {
        try {
            switch (WhereOperator.from(key)) {
                case LT:
                    return "$lt";
                case LTE:
                    return "$lte";
                case GT:
                    return "$gt";
                case GTE:
                    return "$gte";
                case NE:
                    return "$ne";
                case AND:
                    return "$and";
                case OR:
                    return "$or";
                case EQ:
                    return "$eq";
                default:
                    return key;
            }
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    public static void main(String[] args) {
        EmbeddingWhere where = EmbeddingWhere.builder()
                .in("file-id", Arrays.asList("1", "2", "3"))
                .notIn("file-name", Arrays.asList("4", "5", "6"))
                .build();
        QueryRequest request = new QueryRequest(null, 0, where);
        System.out.println(Json.toJson(request.getWhere()));
    }
}
