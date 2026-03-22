package dev.langchain4j.store.embedding.milvus.v2;

public class FieldDefinition {

    final String idFieldName;

    final String textFieldName;

    final String metadataFieldName;

    // dense vector
    final String vectorFieldName;
    // sparse vector
    final String sparseVectorFieldName;

    public FieldDefinition(
            String idFieldName,
            String textFieldName,
            String metadataFieldName,
            String vectorFieldName,
            String sparseVectorFieldName) {
        this.idFieldName = idFieldName;
        this.textFieldName = textFieldName;
        this.metadataFieldName = metadataFieldName;
        this.vectorFieldName = vectorFieldName;
        this.sparseVectorFieldName = sparseVectorFieldName;
    }

    public String getIdFieldName() {
        return idFieldName;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public String getMetadataFieldName() {
        return metadataFieldName;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public String getSparseVectorFieldName() {
        return sparseVectorFieldName;
    }
}
