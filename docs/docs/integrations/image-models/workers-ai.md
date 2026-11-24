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

`WorkersAiImageModel` edits images given by URL or as base64 data. It downloads images given by URL with the
`MediaFetcher` set with `mediaFetcher(...)` on the builder, which by default downloads only `http` and `https` URLs of
public hosts and never reads local files, see [Media given by URL](/tutorials/chat-and-language-models#media-given-by-url).
To edit a local image, create it from the content of the file:

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
