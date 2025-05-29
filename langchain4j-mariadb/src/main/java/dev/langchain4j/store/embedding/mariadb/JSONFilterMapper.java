package dev.langchain4j.store.embedding.mariadb;

class JSONFilterMapper extends MariaDbFilterMapper {
    final String metadataColumn;

    public JSONFilterMapper(String metadataColumn) {
        this.metadataColumn = metadataColumn;
    }

    String formatKey(String key) {
        return "JSON_VALUE(" + this.metadataColumn + ", '$." + key + "')";
    }
}
