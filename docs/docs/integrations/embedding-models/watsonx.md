---
sidebar_position: 23
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai#text-embeddings)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)
- [watsonx.ai Java SDK documentation](https://ibm.github.io/watsonx-ai-java-sdk/services/embedding-service)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## Authentication

Watsonx.ai supports authentication via the `Authenticator` interface.

This allows you to use different authentication mechanisms depending on your deployment:

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

## Listeners

`WatsonxEmbeddingModel` and `WatsonxGatewayEmbeddingModel` both accept a list of `EmbeddingModelListener`, notified before every request, after every response and on every error.

```java
EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .listeners(List.of(new EmbeddingModelListener() {

        @Override
        public void onRequest(EmbeddingModelRequestContext context) {
            System.out.println(context.embeddingRequest().inputs().size() + " input(s)");
        }

        @Override
        public void onResponse(EmbeddingModelResponseContext context) {
            System.out.println(context.embeddingResponse().tokenUsage());
        }

        @Override
        public void onError(EmbeddingModelErrorContext context) {
            System.out.println(context.error().getMessage());
        }
    }))
    .build();
```

## Model Gateway

The IBM watsonx.ai **Model Gateway** exposes an embeddings endpoint compatible with the OpenAI one, able to route requests to models hosted by several providers (for example OpenAI, or the external providers you register yourself) behind a single watsonx.ai entry point. LangChain4j integrates with it through `WatsonxGatewayEmbeddingModel`.

> **Note:** the gateway must be configured by an administrator before use. Every `modelName` you pass must be an id already registered in the gateway.

### WatsonxGatewayEmbeddingModel

To create an instance, specify:

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`)
- `apiKey(...)` – IBM Cloud IAM API key (or a full `Authenticator` via `.authenticator(...)`)
- `modelName(...)` – embedding model id registered in the gateway, written in the OpenAI style

No `projectId` or `spaceId` is required, the gateway resolves the model on its own.

```java
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayEmbeddingModel;
import com.ibm.watsonx.ai.CloudRegion;

EmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("text-embedding-3-small")
    .build();

System.out.println(embeddingModel.embed("Hello from the watsonx.ai Model Gateway"));
```

### Gateway-only parameters

The gateway accepts three parameters that the watsonx.ai embeddings endpoint does not have. They can be set once on the builder and apply to every request.

| Builder method | Description |
|---|---|
| `dimensions(...)` | Number of dimensions of the returned vectors, for the models that support it |
| `encodingFormat(...)` | Wire format of the returned vectors, `FLOAT` or `BASE64`. The SDK decodes `BASE64` for you, so both formats give the same vectors |
| `user(...)` | Identifier of the end user, used for abuse monitoring |

```java
EmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("text-embedding-3-small")
    .dimensions(512)
    .encodingFormat(EncodingFormat.BASE64)
    .build();
```

The same values can be passed per request through `ModelGatewayEmbeddingParameters`. The given parameters replace the ones set on the builder, they are not merged with them.

```java
WatsonxGatewayEmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("text-embedding-3-small")
    .build();

ModelGatewayEmbeddingParameters parameters = ModelGatewayEmbeddingParameters.builder()
    .dimensions(256)
    .build();

Response<List<Embedding>> response = embeddingModel.embedAll(
    List.of(TextSegment.from("Hello"), TextSegment.from("World")), parameters);
```

`dimensions` is the only one of the three that is also part of the LangChain4j embedding request API, so it can be set per request without leaving the `EmbeddingModel` interface. The value of the request overrides the one of the builder, while `encodingFormat` and `user` keep the values set on the builder.

```java
EmbeddingResponse response = embeddingModel.embed(EmbeddingRequest.builder()
    .input("Hello from the watsonx.ai Model Gateway")
    .dimensions(256)
    .build());
```

## Examples

- [WatsonxEmbeddingModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxEmbeddingModelTest.java)