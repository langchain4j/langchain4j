# Observability with Micrometer

The `langchain4j-micrometer-metrics` module provides a Micrometer-based metrics implementation for the `langchain4j` library. Currently, it provides metrics for chat model interactions using a `ChatModelListener` implementation that collects metrics via Micrometer's `MeterRegistry`.

The naming of the metrics follows the [OpenTelemetry Semantic Conventions for Generative AI Metrics](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/). (v1.39.0)

> **⚠️ Experimental**: This module is marked as `@Experimental` and may have breaking changes in future versions.

> **⚠️ Warning**: The OpenTelemetry Semantic Conventions for Generative AI are currently **experimental and not stable**. This means they may have breaking changes in future versions. If you follow these conventions, you may need to introduce breaking changes to your dashboards, alerts, and automations when the conventions are updated.

## Metrics

The following metrics are currently collected:

| Metric Name | Type | Description |
|-------------|------|-------------|
| `gen_ai.client.token.usage` | Counter | The number of tokens used by the model for input or output |

### Tags on `gen_ai.client.token.usage`

| Tag | Description | Example Values |
|-----|-------------|----------------|
| `gen_ai.operation.name` | The operation being performed | `chat` |
| `gen_ai.system` | The AI system/provider name | `openai`, `azure_openai`, `anthropic` |
| `gen_ai.request.model` | The model name from the request | `gpt-4`, `gpt-35-turbo` |
| `gen_ai.response.model` | The model name from the response | `gpt-4-0613` |
| `gen_ai.token.type` | The type of token counted | `input`, `output` |

## Usage
First add the `langchain4j-micrometer-metrics` dependency to your project:

For Maven:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-micrometer-metrics</artifactId>
    <version>${project.version}</version>
</dependency>
```
For Gradle:
```gradle
implementation 'dev.langchain4j:langchain4j-micrometer-metrics:${project.version}'
```

### Micrometer (Actuator) Configuration
You should also have the necessary Actuator dependency in your project. For example, if you are using Spring Boot, you can add the following dependencies to your `pom.xml`:

For Maven:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
For Gradle:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

Enable the `/metrics` Actuator endpoint in your properties.

application.properties:
```properties
management.endpoints.web.exposure.include=metrics
```
application.yaml:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics
```

### Add observability to your ChatModel

The `ChatModelMicrometerMetricsListener` collects metrics for chat model interactions. It requires a `MeterRegistry` (provided by Micrometer) and an AI system name that identifies the provider.

**Important**: The `aiSystemName` parameter should follow the OpenTelemetry Semantic Convention values for `gen_ai.system`. Common values include:
- `openai` - for OpenAI
- `azure_openai` - for Azure OpenAI
- `anthropic` - for Anthropic Claude
- `vertex_ai` - for Google Vertex AI

```java
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.metrics.listeners.ChatModelMicrometerMetricsListener;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

// Get the MeterRegistry (from Spring context or create manually)
MeterRegistry meterRegistry = ...; // e.g., new SimpleMeterRegistry() or injected from Spring

// 1. Create the listener with the MeterRegistry and AI system name
ChatModelMicrometerMetricsListener listener = 
    new ChatModelMicrometerMetricsListener(meterRegistry, "azure_openai");

// 2. Add the listener to your ChatModel
AzureOpenAiChatModel chatModel = AzureOpenAiChatModel.builder()
        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
        .listeners(List.of(listener))
        .build();

// 3. Use the chat model as usual - metrics are collected automatically
ChatResponse response = chatModel.chat(ChatRequest.builder()
        .messages(UserMessage.from("Hello!"))
        .build());
```

#### Spring Boot Integration

In a Spring Boot application, you can create the listener as a bean and inject the `MeterRegistry`:

```java
import dev.langchain4j.micrometer.metrics.listeners.ChatModelMicrometerMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public ChatModelMicrometerMetricsListener chatModelMetricsListener(MeterRegistry meterRegistry) {
        return new ChatModelMicrometerMetricsListener(meterRegistry, "azure_openai");
    }
}
```

## Viewing the Metrics

You can view the metrics by visiting the `/actuator/metrics` endpoint of your application. 

For example, if you are running your application on `localhost:8080`, you can visit `http://localhost:8080/actuator/metrics` to view the metrics.

### Token Usage Metric

View the token usage metric at:
```
/actuator/metrics/gen_ai.client.token.usage
```

#### Filtering by Token Type

The `gen_ai.token.type` tag indicates whether the tokens were used for input or output:

| Token Type | Endpoint |
|------------|----------|
| Input tokens | `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:input` |
| Output tokens | `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:output` |

> **Note**: The endpoint for the `gen_ai.client.token.usage` metric without any tags shows the sum of all token usage (both input and output tokens across all models and systems).

## Error Handling

When a chat model request fails with an error, no token usage metrics are recorded. This is because the `ChatModelErrorContext` does not contain the response with token usage information.

## AI System Name according to OpenTelemetry Semantic Conventions

The `aiSystemName` parameter should use values from the [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/attributes-registry/gen-ai/):

| AI Provider  | `aiSystemName` Value |
|--------------|----------------------|
| OpenAI       | `openai`             |
| Azure OpenAI | `azure.ai.openai`    |
| Anthropic    | `anthropic`          |
| Gemini       | `gcp.gemini`         |
| AWS Bedrock  | `aws.bedrock`        |
| Cohere       | `cohere`             |
| Mistral AI   | `mistral_ai`         |
