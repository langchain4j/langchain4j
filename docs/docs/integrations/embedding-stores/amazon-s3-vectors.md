---
sidebar_position: 29
---

# Amazon S3 Vectors

The Amazon S3 Vectors Embedding Store integrates with [Amazon S3 Vectors](https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors.html), a purpose-built vector storage capability within Amazon S3 designed for storing and querying vector embeddings at scale.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-s3-vectors</artifactId>
    <version>${latest version here}</version>
</dependency>
```

## APIs

- `S3VectorsEmbeddingStore`

## Features

- Store embeddings with metadata
- Vector similarity search with cosine or euclidean distance
- Filter search results by metadata fields
- Automatic index creation on first embedding insertion
- Standard AWS credential provider support

## Usage

### Basic Configuration

```java
S3VectorsEmbeddingStore embeddingStore = S3VectorsEmbeddingStore.builder()
    .vectorBucketName("my-vector-bucket")       // S3 Vectors bucket name (required)
    .indexName("my-index")                       // Index name within the bucket (required)
    .region("us-west-2")                         // AWS region (default: us-east-1)
    .distanceMetric(DistanceMetric.COSINE)       // Distance metric (default: COSINE)
    .createIndexIfNotExists(true)                // Auto-create index (default: true)
    .timeout(Duration.ofSeconds(60))             // API call timeout (default: 30 seconds)
    .credentialsProvider(myCredentialsProvider)  // Custom AWS credentials
    .build();
```

### Using an Existing S3VectorsClient

If you already have a configured S3VectorsClient, you can pass it directly to the builder:

```java
S3VectorsClient customClient = S3VectorsClient.builder()
    .region(Region.US_WEST_2)
    .credentialsProvider(myCredentialsProvider)
    .build();

S3VectorsEmbeddingStore embeddingStore = S3VectorsEmbeddingStore.builder()
    .s3VectorsClient(customClient)
    .vectorBucketName("my-vector-bucket")
    .indexName("my-index")
    .build();
```

## Distance Metrics

S3 Vectors embedding store supports two distance metrics. The distance values are automatically converted to relevance scores in the range [0, 1], where 1 represents the most relevant match.

### Cosine Distance (Default)

**Best for:** Text embeddings, semantic similarity search

- Measures the cosine of the angle between vectors
- Converted to relevance score: `score = (1 - distance + 1) / 2`
- Results are independent of vector magnitude

```java
.distanceMetric(DistanceMetric.COSINE)  // Default, recommended for text embeddings
```

### Euclidean Distance

**Best for:** When both direction and magnitude matter

- Measures straight-line distance between vectors
- Range: [0, ∞)
- Converted to relevance score: `score = 1 / (1 + distance)`

```java
.distanceMetric(DistanceMetric.EUCLIDEAN)
```

## Filtering

S3 Vectors embedding store supports filtering search results by metadata fields.

### Supported Filter Operations

- `isEqualTo`: Equal comparison
- `isNotEqualTo`: Not equal comparison
- `isGreaterThan`: Greater than comparison
- `isGreaterThanOrEqualTo`: Greater than or equal comparison
- `isLessThan`: Less than comparison
- `isLessThanOrEqualTo`: Less than or equal comparison
- `isIn`: IN operator (multiple values)
- `isNotIn`: NOT IN operator
- `And`: Logical AND
- `Or`: Logical OR
- `Not`: Logical NOT

## Implementation Details

### Credentials

By default, the store uses `DefaultCredentialsProvider` which follows the standard AWS credential resolution chain (environment variables, system properties, credential files, EC2 instance profile, etc.). You can provide a custom `AwsCredentialsProvider` via the builder.

### Index Creation

When `createIndexIfNotExists` is set to `true` (default), the index is created automatically on the first embedding insertion. The index dimension and distance metric are set based on the first embedding added and the configured distance metric.

### Resource Cleanup

`S3VectorsEmbeddingStore` implements `AutoCloseable`. When you're done using the store, call `close()` to release the underlying S3VectorsClient resources, or use try-with-resources.

## Limitations

- **Maximum Results**: S3 Vectors limits search results to 100 per query (topK range: 1-100)
- **Remove by Filter**: `removeAll(Filter)` is not supported; use `removeAll(Collection<String> ids)` instead
- **Remove All**: `removeAll()` deletes the entire index

## Examples

- [S3VectorsEmbeddingStoreIT](https://github.com/langchain4j/langchain4j-community/blob/main/embedding-stores/langchain4j-community-s3-vectors/src/test/java/dev/langchain4j/community/store/embedding/s3/S3VectorsEmbeddingStoreIT.java)
