package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.store.embedding.vearch.field.Field;
import dev.langchain4j.store.embedding.vearch.field.FieldType;
import dev.langchain4j.store.embedding.vearch.field.StringField;
import dev.langchain4j.store.embedding.vearch.field.VectorField;
import dev.langchain4j.store.embedding.vearch.index.HNSWParam;
import dev.langchain4j.store.embedding.vearch.index.Index;
import dev.langchain4j.store.embedding.vearch.index.IndexType;
import dev.langchain4j.store.embedding.vearch.index.search.SearchIndexParam;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class VearchConfig {

    static final String DEFAULT_ID_FIELD_NAME = "_id";
    static final String DEFAULT_EMBEDDING_FIELD_NAME = "embedding";
    static final String DEFAULT_TEXT_FIELD_NAME = "text";
    static final String DEFAULT_SCORE_FILED_NAME = "_score";

    static final List<Field> DEFAULT_FIELDS = List.of(
        VectorField.builder()
            .name(DEFAULT_EMBEDDING_FIELD_NAME)
            .dimension(384)
            .index(Index.builder()
                .name("gamma")
                .type(IndexType.HNSW)
                .params(HNSWParam.builder()
                    .metricType(MetricType.INNER_PRODUCT)
                    .efSearch(64)
                    .build())
                .build()).build(),
        StringField.builder()
            .fieldType(FieldType.STRING)
            .name(DEFAULT_TEXT_FIELD_NAME)
            .build()
    );

    private String databaseName;
    private String spaceName;
    /**
     * Index param when searching, if not set, will use {@link Index}.
     *
     * @see Index
     */
    private SearchIndexParam searchIndexParam;
    /**
     * This attribute's key set should contain
     * {@link VearchConfig#embeddingFieldName}, {@link VearchConfig#textFieldName} and {@link VearchConfig#metadataFieldNames}
     */
    private List<Field> fields;
    private String embeddingFieldName;
    private String textFieldName;
    /**
     * This attribute should be the subset of {@link VearchConfig#fields}'s key set
     */
    private List<String> metadataFieldNames;

    public VearchConfig(Builder builder) {
        this.databaseName = ensureNotNull(builder.databaseName, "databaseName");
        this.spaceName = ensureNotNull(builder.spaceName, "spaceName");
        this.searchIndexParam = builder.searchIndexParam;
        this.fields = getOrDefault(builder.fields, DEFAULT_FIELDS);
        this.embeddingFieldName = getOrDefault(builder.embeddingFieldName, DEFAULT_EMBEDDING_FIELD_NAME);
        this.textFieldName = getOrDefault(builder.textFieldName, DEFAULT_TEXT_FIELD_NAME);
        this.metadataFieldNames = builder.metadataFieldNames;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public SearchIndexParam getSearchIndexParam() {
        return searchIndexParam;
    }

    public void setSearchIndexParam(SearchIndexParam searchIndexParam) {
        this.searchIndexParam = searchIndexParam;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public String getEmbeddingFieldName() {
        return embeddingFieldName;
    }

    public void setEmbeddingFieldName(String embeddingFieldName) {
        this.embeddingFieldName = embeddingFieldName;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public void setTextFieldName(String textFieldName) {
        this.textFieldName = textFieldName;
    }

    public List<String> getMetadataFieldNames() {
        return metadataFieldNames;
    }

    public void setMetadataFieldNames(List<String> metadataFieldNames) {
        this.metadataFieldNames = metadataFieldNames;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String databaseName;
        private String spaceName;
        private SearchIndexParam searchIndexParam;
        private List<Field> fields;
        private String embeddingFieldName;
        private String textFieldName;
        private List<String> metadataFieldNames;

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder spaceName(String spaceName) {
            this.spaceName = spaceName;
            return this;
        }

        public Builder searchIndexParam(SearchIndexParam searchIndexParam) {
            this.searchIndexParam = searchIndexParam;
            return this;
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder embeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
            return this;
        }

        public Builder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        public Builder metadataFieldNames(List<String> metadataFieldNames) {
            this.metadataFieldNames = metadataFieldNames;
            return this;
        }

        public VearchConfig build() {
            return new VearchConfig(this);
        }
    }
}
