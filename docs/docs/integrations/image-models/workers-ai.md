---
sidebar_position: 6
---

# Cloudflare Workers AI

https://developers.cloudflare.com/workers-ai/


```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-workers-ai</artifactId>
    <version>1.24.0-beta34</version>
</dependency>
```

## APIs

- `WorkersAiImageModel`

`WorkersAiImageModel` edits images given as base64 data. Images given by URL are not supported. To edit a local
image, create it from the content of the file:

```java
Image image = Image.builder()
        .base64Data(Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("/home/me/cat.png"))))
        .mimeType("image/png")
        .build();
```

It decodes PNG, JPEG, GIF, BMP and WebP images (WebP only if an image reader for it is installed) of at most
2048 x 2048 pixels, a limit of LangChain4j against very large decoded images.


## Examples

- [WorkersAiImageModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-workers-ai/src/test/java/dev/langchain4j/model/workersai/WorkersAiImageModelIT.java)
