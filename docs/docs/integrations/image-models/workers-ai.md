---
sidebar_position: 6
---

# Cloudflare Workers AI

https://developers.cloudflare.com/workers-ai/


```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-workers-ai</artifactId>
    <version>1.23.0-beta33</version>
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

The image and the mask are sent to Workers AI as the bytes of their files, so Workers AI checks their format and size.


## Examples

- [WorkersAiImageModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-workers-ai/src/test/java/dev/langchain4j/model/workersai/WorkersAiImageModelIT.java)
