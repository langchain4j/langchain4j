package dev.langchain4j.store.embedding;

public class CollectionDescription {

    private String collectionName;
    private String idFieldName;
    private String vectorFieldName;
    private String scalarFieldName;

    public CollectionDescription(String collectionName, String idFieldName, String vectorFieldName, String scalarFieldName) {
        this.collectionName = collectionName;
        this.idFieldName = idFieldName;
        this.vectorFieldName = vectorFieldName;
        this.scalarFieldName = scalarFieldName;
    }

    public static CollectionDescriptionBuilder builder() {
        return new CollectionDescriptionBuilder();
    }

    public String getCollectionName() {
        return this.collectionName;
    }

    public String getIdFieldName() {
        return this.idFieldName;
    }

    public String getVectorFieldName() {
        return this.vectorFieldName;
    }

    public String getScalarFieldName() {
        return this.scalarFieldName;
    }

    public static class CollectionDescriptionBuilder {
        private String collectionName;
        private String idFieldName;
        private String vectorFieldName;
        private String scalarFieldName;

        CollectionDescriptionBuilder() {
        }

        public CollectionDescriptionBuilder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public CollectionDescriptionBuilder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        public CollectionDescriptionBuilder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        public CollectionDescriptionBuilder scalarFieldName(String scalarFieldName) {
            this.scalarFieldName = scalarFieldName;
            return this;
        }

        public CollectionDescription build() {
            return new CollectionDescription(this.collectionName, this.idFieldName, this.vectorFieldName, this.scalarFieldName);
        }

        public String toString() {
            return "CollectionDescription.CollectionDescriptionBuilder(collectionName=" + this.collectionName + ", idFieldName=" + this.idFieldName + ", vectorFieldName=" + this.vectorFieldName + ", scalarFieldName=" + this.scalarFieldName + ")";
        }
    }

}
