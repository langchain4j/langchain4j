---
sidebar_position: 1
---

# In-process (ONNX)

LangChain4j provides a few popular local embedding models packaged as maven dependencies.
They are powered by [ONNX runtime](https://onnxruntime.ai/docs/get-started/with-java.html)
and are running in the same java process.

Each model is provided in 2 flavours: original and quantized (has a `-q` suffix in maven artifact name and `Quantized` in the class name).

The complete list of all embedding models can be found [here](https://github.com/langchain4j/langchain4j-embeddings).


## Custom models

Many models (e.g., from [Hugging Face](https://huggingface.co/)) can be used,
as long as they are in the ONNX format.

Information on how to convert models into ONNX format can be found [here](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model).

Many models already converted to ONNX format are available [here](https://huggingface.co/Xenova).


## Examples

- [InProcessEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/InProcessEmbeddingModelExamples.java)