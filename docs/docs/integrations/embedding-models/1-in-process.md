---
sidebar_position: 1
---

# In-process (ONNX)

LangChain4j provides a few popular local embedding models packaged as maven dependencies.
They are powered by [ONNX runtime](https://onnxruntime.ai/docs/get-started/with-java.html)
and are running in the same java process.

Each model is provided in 2 flavours: original and quantized (has a `-q` suffix in maven artifact name and `Quantized` in the class name).

For example:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
    <version>0.34.0</version>
</dependency>
```
```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
Response<Embedding> response = embeddingModel.embed("test");
Embedding embedding = response.content();
```

Or quantized:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2-q</artifactId>
    <version>0.34.0</version>
</dependency>
```
```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
Response<Embedding> response = embeddingModel.embed("test");
Embedding embedding = response.content();
```

The complete list of all embedding models can be found [here](https://github.com/langchain4j/langchain4j-embeddings).


## Parallelization

By default, the embedding process is parallelized using all available CPU cores,
so each `TextSegment` is embedded in a separate thread.

The parallelization is done by using an `Executor`.
By default, in-process embedding models use a cached thread pool
with the number of threads equal to the number of available processors.
Threads are cached for 1 second.

You can provide a custom instance of the `Executor` when creating a model:
```java
Executor = ...;
EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel(executor);
```

Embedding using GPU is not supported yet.

## Custom models

Many models (e.g., from [Hugging Face](https://huggingface.co/)) can be used,
as long as they are in the ONNX format.

Information on how to convert models into ONNX format can be found [here](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model).

Many models already converted to ONNX format are available [here](https://huggingface.co/Xenova).

Example of using custom embedding model:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings</artifactId>
    <version>0.34.0</version>
</dependency>
```
```java
String pathToModel = "/home/langchain4j/model.onnx";
String pathToTokenizer = "/home/langchain4j/tokenizer.json";
PoolingMode poolingMode = PoolingMode.MEAN;
EmbeddingModel embeddingModel = new OnnxEmbeddingModel(pathToModel, pathToTokenizer, poolingMode);

Response<Embedding> response = embeddingModel.embed("test");
Embedding embedding = response.content();
```

## Examples

- [InProcessEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/InProcessEmbeddingModelExamples.java)