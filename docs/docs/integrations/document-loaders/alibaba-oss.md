---
sidebar_position: 8
---

# Alibaba OSS


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-loader-alibaba-oss</artifactId>
    <version>1.0.0-beta1</version>
</dependency>
```


## APIs

- `AlibabaOssDocumentLoader`
  - loadDocument: Loads a single document from the specified OSS bucket based on the specified object key.
  - loadDocuments: Loads all documents from an OSS bucket.Skips any documents that fail to load.
  - builder: build AlibabaOssDocumentLoader


## Examples

```java
AlibabaOssDocumentLoader loader = AlibabaOssDocumentLoader.builder()
        .endpoint("your-endpoint")
        .region("cn-hangzhou")
        .alibabaOssCredentials(new AlibabaOssCredentials("accessKey", "secretKey",null))
        .build();

Document document = loader.loadDocument("your-bucket", "path/to/file.txt", new TextDocumentParser());

```
See more
- [AlibabaOssDocumentLoaderIT](https://github.com/langchain4j/langchain4j/blob/main/https://github.com/langchain4j/langchain4j/blob/main/document-loaders/langchain4j-document-loader-alibaba-oss/src/test/java/dev/langchain4j/data/document/loader/alibaba/oss/AlibabaOssDocumentLoaderIT.java)
