---
sidebar_position: 1
---

# In-process (ONNX)

LangChain4j provides local scoring (reranking) models,
powered by [ONNX runtime](https://onnxruntime.ai/docs/get-started/with-java.html), running in the same Java process.

Many models (e.g., from [Hugging Face](https://huggingface.co/)) can be used,
as long as they are in the ONNX format.

Information on how to convert models into ONNX format can be found [here](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model).

Many models already converted to ONNX format are available [here](https://huggingface.co/Xenova).

### Usage

By default, scoring (reranking) model uses the CPU. 
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-onnx-scoring</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```
```java
String pathToModel = "/home/langchain4j/model.onnx";
String pathToTokenizer = "/home/langchain4j/tokenizer.json";
OnnxScoringModel scoringModel = new OnnxScoringModel(pathToModel, pathToTokenizer);

Response<Double> response = scoringModel.score("query", "passage");
Double score = response.content();
```

If you want to use the GPU, `onnxruntime_gpu` version can be found
[here](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html).
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-onnx-scoring</artifactId>
    <version>1.0.1-beta6</version>
    <exclusions>
        <exclusion>
            <groupId>com.microsoft.onnxruntime</groupId>
            <artifactId>onnxruntime</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- 1.18.0 support CUDA 12.x -->
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime_gpu</artifactId>
    <version>1.18.0</version>
</dependency>
```

```java
String pathToModel = "/home/langchain4j/model.onnx";
String pathToTokenizer = "/home/langchain4j/tokenizer.json";

OrtSession.SessionOptions options = new OrtSession.SessionOptions();
options.addCUDA(0);
OnnxScoringModel scoringModel = new OnnxScoringModel(pathToModel, options, pathToTokenizer);

Response<Double> response = scoringModel.score("query", "passage");
Double score = response.content();
```
