package dev.langchain4j.store.embedding.milvus;


class FieldDefinition {

    String idFieldName;

    String textFieldName;

    String metadataFieldName;

    // dense vector
    String vectorFieldName;
    // sparse vector
    String sparseVectorFieldName;

    public FieldDefinition(String idFieldName, String textFieldName, String metadataFieldName, String vectorFieldName, String sparseVectorFieldName) {
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
