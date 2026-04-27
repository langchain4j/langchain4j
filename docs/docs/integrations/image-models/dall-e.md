---
sidebar_position: 2
---

# OpenAI (DALL·E and GPT Image)

:::note

This is the documentation for the `OpenAI` integration, that uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

The same `OpenAiImageModel` class is used for both the legacy `dall-e-*` models and the newer `gpt-image-*` models. Image-edit support targets `gpt-image-1` and `gpt-image-2` (which accept multiple input images and an optional mask) as well as `dall-e-2` (single-image edit only).

LangChain4j provides 3 different integrations with OpenAI for generating images, and this is #1 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

:::

## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.13.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.13.0-beta23</version>
</dependency>
```


## Creating `OpenAiImageModel`

### Plain Java

For DALL·E 3 generation:

```java
ImageModel model = OpenAiImageModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("dall-e-3")
        .build();
```

For a `gpt-image-*` model, you can use the enum and the `gpt-image-*`-specific options:

```java
import static dev.langchain4j.model.openai.OpenAiImageModelName.GPT_IMAGE_2;

ImageModel model = OpenAiImageModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_IMAGE_2)
        .size("1024x1024")
        .quality("high")           // gpt-image-* only: low | medium | high | auto
        .background("transparent") // gpt-image-* only: transparent | opaque | auto
        .outputFormat("png")       // gpt-image-* only: png | jpeg | webp
        .build();
```

`gpt-image-*` models always return base64-encoded images; `response_format` is omitted from the request automatically (the API rejects it for these models).

### Spring Boot
Add to the `application.properties`:
```properties
# Mandatory properties:
langchain4j.open-ai.image-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.image-model.model-name=dall-e-3

# Optional properties:
langchain4j.open-ai.image-model.base-url=...
langchain4j.open-ai.image-model.custom-headers=...
langchain4j.open-ai.image-model.log-requests=...
langchain4j.open-ai.image-model.log-responses=...
langchain4j.open-ai.image-model.max-retries=...
langchain4j.open-ai.image-model.organization-id=...
langchain4j.open-ai.image-model.project-id=...
langchain4j.open-ai.image-model.quality=...
langchain4j.open-ai.image-model.response-format=...
langchain4j.open-ai.image-model.size=...
langchain4j.open-ai.image-model.style=...
langchain4j.open-ai.image-model.timeout=...
langchain4j.open-ai.image-model.user=...
```

## Image Editing

`OpenAiImageModel` exposes the `/v1/images/edits` endpoint via the `edit(...)` overloads on `ImageModel`. Input images must be supplied as base64 (set `Image.base64Data()` and `Image.mimeType()`); URL-only `Image` values are rejected with a clear error — fetch and encode them at the call site.

### Single-image edit

```java
Image source = Image.builder()
        .base64Data(base64PngBytes)
        .mimeType("image/png")
        .build();

Response<Image> response = model.edit(source, "Add a smiling cartoon sun in the upper-right corner");
String editedBase64 = response.content().base64Data();
```

### Edit with a mask

The masked region of the input is the area the model is allowed to alter. Mask must be a PNG with an alpha channel.

```java
Response<Image> response = model.edit(source, mask, "Replace the masked area with a small red apple");
```

### Multi-image edit (gpt-image-* only)

`gpt-image-*` models accept up to 16 input images per edit request. Each input is sent as a separate multipart part.

```java
import static dev.langchain4j.model.openai.OpenAiImageModelName.GPT_IMAGE_2;

ImageModel model = OpenAiImageModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_IMAGE_2)
        .build();

List<Image> sources = List.of(bodyLotion, bathBomb, incenseKit, soap);
Response<Image> response = model.edit(sources, "Compose a gift basket with these four items in it");
```

### Multiple variations + mask in one call

The canonical `edit(...)` overload exposes everything at once — use this when you need both a mask and `n > 1`:

```java
Response<List<Image>> response = model.edit(
        List.of(source),
        mask,
        "Replace the masked area with a small red apple",
        2 // number of variations to return
);
```

### Edit-specific builder options

Beyond the generation options shown above, the builder exposes:

- `inputFidelity("high" | "low")` — gpt-image-1 only. Silently dropped for `gpt-image-2`, which always processes inputs at high fidelity automatically and rejects this parameter.
- `moderation("low" | "auto")` — gpt-image-* only.
- `outputCompression(0..100)` — gpt-image-* only, for `webp`/`jpeg` output.

## Token Usage

`gpt-image-*` responses include a `usage` block with a per-direction text/image token breakdown. `Response<Image>.tokenUsage()` returns an `OpenAiImageTokenUsage` (a `TokenUsage` subclass) that exposes those details:

```java
Response<Image> response = model.edit(source, "...");
if (response.tokenUsage() instanceof OpenAiImageTokenUsage usage) {
    int totalTokens = usage.totalTokenCount();
    int inputImageTokens = usage.inputTokensDetails().imageTokens();
    int inputTextTokens = usage.inputTokensDetails().textTokens();
    int outputImageTokens = usage.outputTokensDetails().imageTokens();
}
```

`dall-e-*` responses don't include a usage block, so `tokenUsage()` is `null` on those paths.

## Examples

- [OpenAiImageModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiImageModelExamples.java)
