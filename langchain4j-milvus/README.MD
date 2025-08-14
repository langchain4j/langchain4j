# Milvus Embedding Store

This module implements `EmbeddingStore` using Milvus Vector Database.

## Requirements

- Milvus 2.4.20 or newer

## Running the Test Suite

The test suites can run on [Docker Testcontainers](https://java.testcontainers.org/modules/milvus/) or a [Zilliz Cloud](https://zilliz.com/cloud) cluster. For the cloud target, make sure the following environment variables are configured:
- MILVUS_URI
- MILVUS_API_KEY
- MILVUS_USERNAME
- MILVUS_PASSWORD
