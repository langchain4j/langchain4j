---
sidebar_position: 23
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai#text-embeddings)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.10.0-beta18</version>
</dependency>
```

## Authentication

Watsonx.ai supports authentication via the `Authenticator` interface.

This allows to use different authentication mechanisms depending on your deployment:

- **IBMCloudAuthenticator** – authenticates with **IBM Cloud** using an API key. This is the simplest approach and is used when you provide the `apiKey(...)` builder method.
- **CP4DAuthenticator** – authenticates with **Cloud Pak for Data** deployments.
- **Custom authenticators** – any implementation of the `Authenticator` interface can be used.

The `WatsonxEmbeddingModel` and other service builders accept either a shortcut via `.apiKey(...)` or a full `Authenticator` instance via `.authenticator(...)`.

### Example
```java
WatsonxEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key") // Simple IBM Cloud authentication
    .projectId("your-project-id")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .build();

WatsonxEmbeddingModel.builder()
    .baseUrl("https://my-instance-url")
    .authenticator( // For Cloud Pak for Data deployments
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .build()
    )
    .projectId("my-project-id")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .build();
```

### Custom HttpClient and SSL Configuration

#### Using a custom HttpClient

All services and authenticators support a custom `HttpClient` instance through the builder pattern. This is particularly useful for Cloud Pak for Data environments where you may need to configure custom TLS/SSL settings, proxy configuration, or other HTTP client properties.

```java
HttpClient httpClient = HttpClient.newBuilder()
    .sslContext(createCustomSSLContext())
    .executor(ExecutorProvider.ioExecutor())
    .build();

EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl("https://my-instance-url")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .projectId("project-id")
    .httpClient(httpClient) // Custom HttpClient
    .authenticator(
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .httpClient(httpClient) // Custom HttpClient
            .build()
    )
    .build();
```

> **Note:** When using a custom `HttpClient` with Cloud Pak for Data, make sure to set it on both the service builder and the authenticator builder to ensure consistent HTTP behavior across all requests.

#### Disabling SSL verification

If you only need to disable SSL certificate verification, you can use the `verifySsl(false)` option instead of providing a custom `HttpClient`:

```java
EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl("https://my-instance-url")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .projectId("project-id")
    .verifySsl(false) // Disable SSL verification
    .authenticator(
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .verifySsl(false) // Disable SSL verification
            .build()
    )
    .build();
```

### How to create an IBM Cloud API Key

You can create an API key at [https://cloud.ibm.com/iam/apikeys](https://cloud.ibm.com/iam/apikeys) by clicking **Create +**.

### How to find your Project ID

1. Visit [https://dataplatform.cloud.ibm.com/projects/?context=wx](https://dataplatform.cloud.ibm.com/projects/?context=wx)  
2. Open your project  
3. Go to the **Manage** tab  
4. Copy the **Project ID** from the **Details** section 

## WatsonxEmbeddingModel

The `WatsonxEmbeddingModel` enables you to generate embeddings using IBM watsonx.ai and integrate them with LangChain4j's vector-based operations such as search, retrieval-augmented generation (RAG), and similarity comparison.

It implements the LangChain4j `EmbeddingModel` interface.

```java
EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .build();

System.out.println(embeddingModel.embed("Hello from watsonx.ai"));
```
> 🔗 [View available embedding model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#embed)

## Examples

- [WatsonxEmbeddingModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxEmbeddingModelTest.java)