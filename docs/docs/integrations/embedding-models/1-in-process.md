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
    <version>1.19.0-beta29</version>
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
    <version>1.19.0-beta29</version>
</dependency>
```
```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
Response<Embedding> response = embeddingModel.embed("test");
Embedding embedding = response.content();
```

The complete list of all embedding models can be found [here](https://github.com/langchain4j/langchain4j/tree/main/embeddings).


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
    <version>1.19.0-beta29</version>
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

## Embedding images

Embedding models usually turn text into vectors, but some models can do the same with images.
Embedding an image gives you a vector you can store in an `EmbeddingStore` and search,
which lets you find images that look like another image.

`OnnxImageEmbeddingModel` does this in the same java process, using any vision model in the ONNX format,
such as [CLIP](https://huggingface.co/openai/clip-vit-base-patch32) or ViT:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings</artifactId>
    <version>1.18.1-beta28</version>
</dependency>
```
```java
try (OnnxImageEmbeddingModel embeddingModel = OnnxImageEmbeddingModel.builder()
        .pathToModel("/home/langchain4j/vision_model.onnx")
        .preprocessorConfig(ImagePreprocessorConfig.CLIP)
        .build()) {

    EmbeddingResponse response = embeddingModel.embed(EmbeddingRequest.builder()
            .input(ImageContent.from(Paths.get("/home/langchain4j/cat.png").toUri()))
            .build());

    Embedding embedding = response.embeddings().get(0);
}
```
Images are passed in as `ImageContent`, either as a URL or as base64-encoded data.
Each `input` of the request produces one `Embedding`, so a single call can embed a whole batch of images.
Since this model can only embed images, calling the text methods it inherits from `EmbeddingModel`
(such as `embed("text")`) throws an `UnsupportedFeatureException`.

The model keeps a native ONNX session open, so close it when you are done with it
(the example above does that with a try-with-resources block).

### Preprocessing

Before an image reaches the model it has to be resized, cropped and normalized in exactly the way
the model was trained to expect. Getting this wrong does not cause an error, it silently produces
meaningless embeddings, so it is worth setting up carefully.

`ImagePreprocessorConfig.CLIP` holds the values that CLIP models use, and `ImagePreprocessorConfig.DEFAULT`
the values most other vision models use. For anything else, the values can be found in the
`preprocessor_config.json` file of the model's [Hugging Face](https://huggingface.co/) repository,
and each of them has a matching option on the builder:
```java
ImagePreprocessorConfig config = ImagePreprocessorConfig.builder()
        .imageSize(224)  // the shortest side of the image is resized to this
        .cropSize(224)   // then a square of this size is cut out of the centre
        .imageMean(new float[] {0.5f, 0.5f, 0.5f})
        .imageStd(new float[] {0.5f, 0.5f, 0.5f})
        .build();
```

### Searching images with a text query

Some vision models, CLIP among them, come as a pair: a vision model that embeds images and a text model
that embeds text into *the same* vector space. With such a pair you can search images by describing them
in words, because the embedding of the text "a photo of a cat" ends up close to the embedding of an actual
photo of a cat.

`OnnxImageEmbeddingModel` covers the image half of that pair, and `OnnxEmbeddingModel` the text half.
Load both halves of the same model, embed your images with the first and your query with the second,
and compare the results as usual:
```java
OnnxImageEmbeddingModel imageModel = OnnxImageEmbeddingModel.builder()
        .pathToModel("/home/langchain4j/clip/vision_model.onnx")
        .preprocessorConfig(ImagePreprocessorConfig.CLIP)
        .build();

EmbeddingModel textModel = new OnnxEmbeddingModel(
        "/home/langchain4j/clip/text_model.onnx",
        "/home/langchain4j/clip/tokenizer.json",
        PoolingMode.CLS);

Embedding image = imageModel.embed(EmbeddingRequest.builder()
        .input(ImageContent.from(Paths.get("/home/langchain4j/cat.png").toUri()))
        .build())
        .embeddings().get(0);

Embedding query = textModel.embed("a photo of a cat").content();

double similarity = CosineSimilarity.between(image, query);
```
Both models must come from the *same* pair. Embeddings from two unrelated models are not comparable,
even when they happen to have the same number of dimensions.

## Examples

- [InProcessEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/InProcessEmbeddingModelExamples.java)
