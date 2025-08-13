mvn deploy -T36 \
 -Dmaven.resolver.transport=native -Daether.connector.basic.threads=36 \
 -Dmaven.wagon.httpconnectionManager.maxTotal=200 -Dmaven.wagon.httpconnectionManager.maxPerRoute=200 \
 -DskipTests -DskipITs -DskipAnthropicITs -DskipLocalAiITs -DskipMilvusITs \
 -DskipMongoDbAtlasITs -DskipOllamaITs -DskipVearchITs -DskipVertexAiGeminiITs -Djacoco.skip=true
