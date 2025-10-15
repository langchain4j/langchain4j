---
title: Comparison table of all supported Chat Memory Stores
hide_title: false
sidebar_position: 0
---

| Chat Memory Stores                                                        | Persistence | Default |
|---------------------------------------------------------------------------|-------------|---------|
| [In-memory](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/test/java/dev/langchain4j/store/memory/chat/InMemoryChatMemoryStoreTest.java)                     |             |         |
| [Azure CosmosDB NoSQL](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-cosmos-nosql/src/test/java/dev/langchain4j/store/memory/azure/cosmos/nosql/AzureCosmosDBNoSqlMemoryStoreIT.java) | ✅            |         |
| [Cassandra](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-cassandra/src/test/java/dev/langchain4j/store/memory/chat/cassandra/CassandraChatMemoryStoreDockerIT.java)                     | ✅           |         |
| [Coherence](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-coherence/src/test/java/dev/langchain4j/store/memory/chat/coherence/CoherenceChatMemoryStoreIT.java)                     | ✅           |         |
| [Neo4j](https://github.com/langchain4j/langchain4j-community/blob/main/embedding-stores/langchain4j-community-neo4j/src/test/java/dev/langchain4j/community/store/memory/chat/neo4j/Neo4jChatMemoryStoreIT.java)                             | ✅           |         |
| [Redis](https://github.com/langchain4j/langchain4j-community/blob/main/embedding-stores/langchain4j-community-redis/src/test/java/dev/langchain4j/community/store/memory/chat/redis/RedisChatMemoryStoreIT.java)                             | ✅           |         |
| [Tablestore](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-tablestore/src/test/java/dev/langchain4j/store/memory/chat/tablestore/TablestoreChatMemoryStoreIT.java)                   | ✅           |         |
