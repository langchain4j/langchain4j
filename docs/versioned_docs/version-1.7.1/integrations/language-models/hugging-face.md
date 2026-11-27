---
sidebar_position: 10
---

# Hugging Face

In order to use [Hugging Face Inference Providers](https://huggingface.co/docs/inference-providers/index),
you need to import the `langchain4j-open-ai` module, as Hugging Face Inference Providers API is OpenAI-compatible.


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.7.1</version>
</dependency>
```

## Examples

```java
ChatModel model = OpenAiChatModel.builder()
    .apiKey(System.getenv("HF_API_KEY"))
    .baseUrl("https://router.huggingface.co/v1")
    .modelName("HuggingFaceTB/SmolLM3-3B:hf-inference")
    .build();

StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
    .apiKey(System.getenv("HF_API_KEY"))
    .baseUrl("https://router.huggingface.co/v1")
    .modelName("HuggingFaceTB/SmolLM3-3B:hf-inference")
    .build();
```
