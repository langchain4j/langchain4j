package dev.langchain4j.store.embedding;

import dev.langchain4j.internal.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nottyjay
 */
public class EmbeddingWhere {
    private List<EmbeddingWhereDict> where;

    public EmbeddingWhere(List<EmbeddingWhereDict> where) {
        this.where = where;
    }

    public EmbeddingWhere eq(String field, String value) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.EQ.value(), field, value));
        return this;
    }

    public EmbeddingWhere ne(String field, String value) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.NE.value(), field, value));
        return this;
    }

    public EmbeddingWhere gt(String field, String value) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.GT.value(), field, value));
        return this;
    }

    public EmbeddingWhere lt(String field, String value) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.LT.value(), field, value));
        return this;
    }

    public EmbeddingWhere gte(String field, String value) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.GTE.value(), field, value));
        return this;
    }

    public EmbeddingWhere lte(String field, String value) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.LTE.value(), field, value));
        return this;
    }

    public EmbeddingWhere in(String field, List<Object> values) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.IN.value(), field, values));
        return this;
    }

    public EmbeddingWhere notIn(String field, List<Object> values) {
        this.where.add(new EmbeddingWhereDict(WhereOperator.NOT_IN.value(), field, values));
        return this;
    }

    public List<EmbeddingWhereDict> where() {
        return this.where;
    }

    public static class Builder {
        private List<EmbeddingWhereDict> where;

        public Builder() {
            this.where = new ArrayList<>();
        }

        public Builder eq(String field, String value) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.EQ.value(), field, value));
            return this;
        }

        public Builder ne(String field, String value) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.NE.value(), field, value));
            return this;
        }

        public Builder gt(String field, String value) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.GT.value(), field, value));
            return this;
        }

        public Builder lt(String field, String value) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.LT.value(), field, value));
            return this;
        }

        public Builder gte(String field, String value) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.GTE.value(), field, value));
            return this;
        }

        public Builder lte(String field, String value) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.LTE.value(), field, value));
            return this;
        }

        public Builder in(String field, Object... values) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.IN.value(), field, values));
            return this;
        }

        public Builder in(String field, List<Object> values) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.IN.value(), field, values));
            return this;
        }

        public Builder notIn(String field, Object... values) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.NOT_IN.value(), field, values));
            return this;
        }

        public Builder notIn(String field, List<Object> values) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.NOT_IN.value(), field, values));
            return this;
        }

        public Builder and(EmbeddingWhere where) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.AND.value(), null, where.where()));
            return this;
        }

        public Builder or(EmbeddingWhere where) {
            this.where.add(new EmbeddingWhereDict(WhereOperator.OR.value(), null, where.where()));
            return this;
        }

        public EmbeddingWhere build() {
            return new EmbeddingWhere(this.where);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toString() {
        return "EmbeddingWhere {" +
                "where=" + Json.toJson(where) +
                '}';
    }

    public static class EmbeddingWhereDict {
        private String operator;
        private String field;
        private Object value;

        private EmbeddingWhereDict(String operator, String field, Object value) {
            this.operator = operator;
            this.field = field;
            this.value = value;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}
