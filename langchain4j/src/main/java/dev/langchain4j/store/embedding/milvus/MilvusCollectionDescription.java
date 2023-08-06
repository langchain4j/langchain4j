package dev.langchain4j.store.embedding.milvus;

public class MilvusCollectionDescription {

    private String collectionName;
    private String idFieldName;
    private String vectorFieldName;
    private String scalarFieldName;

    public MilvusCollectionDescription(String collectionName,
                                       String idFieldName,
                                       String vectorFieldName,
                                       String scalarFieldName) {

        if (inputIsValid("collectionName", collectionName)) {
            this.collectionName = collectionName;
        }

        if (inputIsValid("idFieldName", idFieldName)) {
            this.idFieldName = idFieldName;
        }

        if (inputIsValid("vectorFieldName", vectorFieldName)) {
            this.vectorFieldName = vectorFieldName;
        }

        if (inputIsValid("scalarFieldName", scalarFieldName)) {
            this.scalarFieldName = scalarFieldName;
        }

    }

    public String collectionName() {
        return this.collectionName;
    }

    public String idFieldName() {
        return this.idFieldName;
    }

    public String vectorFieldName() {
        return this.vectorFieldName;
    }

    public String scalarFieldName() {
        return this.scalarFieldName;
    }

    public static MilvusCollectionDescriptionBuilder builder() {
        return new MilvusCollectionDescriptionBuilder();
    }

    public static class MilvusCollectionDescriptionBuilder {
        private String collectionName;
        private String idFieldName;
        private String vectorFieldName;
        private String scalarFieldName;

        MilvusCollectionDescriptionBuilder() {
        }

        public MilvusCollectionDescriptionBuilder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public MilvusCollectionDescriptionBuilder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        public MilvusCollectionDescriptionBuilder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        public MilvusCollectionDescriptionBuilder scalarFieldName(String scalarFieldName) {
            this.scalarFieldName = scalarFieldName;
            return this;
        }

        public MilvusCollectionDescription build() {
            return new MilvusCollectionDescription(this.collectionName, this.idFieldName, this.vectorFieldName, this.scalarFieldName);
        }

        public String toString() {
            return "MilvusCollectionDescription.MilvusCollectionDescriptionBuilder(collectionName=" + this.collectionName + ", idFieldName=" + this.idFieldName + ", vectorFieldName=" + this.vectorFieldName + ", scalarFieldName=" + this.scalarFieldName + ")";
        }

    }

    private static boolean inputIsValid(String fieldName, String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("The field '%s' is mandatory%n", fieldName));
        } else {
            return true;
        }
    }

}