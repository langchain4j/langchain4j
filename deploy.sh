mvn deploy -T36 \
 -Daether.connector.http.maxConnectionsPerRoute=200 \
 -DskipTests -DskipITs -DskipAnthropicITs -DskipLocalAiITs -DskipMilvusITs \
 -DskipMongoDbAtlasITs -DskipOllamaITs -DskipVearchITs -DskipVertexAiGeminiITs -Djacoco.skip=true
