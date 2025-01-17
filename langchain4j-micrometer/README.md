# Observability with Micrometer
The `langchain4j-micrometer` module provides a Micrometer-based metrics implementation for the `langchain4j` library. For now, it only provides metrics for a chat model interaction.
It uses the Micrometer Observation API in a `ChatModelListener` to collect metrics about the usage of a chat model. The naming of the metrics is based on the [OpenTelemetry Semantic Conventions for Generative AI Metrics](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/). 

The following metrics are collected:

- `langchain4j.chat.model.request` - The number of requests made to the chat model. Not part of the OpenTelemetry Semantic Conventions.
- `langchain4j.chat.model.error` - The number of errors that soccurred during the chat model interaction. Not part of the OpenTelemetry Semantic Conventions.
- `gen_ai.client.operation.duration` - The duration of the GenAI operation.
- `gen_ai.client.token.usage` - The number of tokens used by the model for input, output, or both.

## Usage
The micrometer module is added to the project from version 1.0.0-alpha2.

First add the `langchain4j-micrometer` dependency to your project:

For Maven:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-micrometer</artifactId>
    <version>${project.version}</version>
</dependency>
```
For Gradle:
```gradle
implementation 'dev.langchain4j:langchain4j-micrometer:${project.version}'
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

Enable the metrics for Actuator in your properties.

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
The `MicrometerChatModelListener` collects the metrics for the chat model. It uses a `ObservationRegistry` and `MeterRegistry` provided by Micrometer to collect the metrics in an Observation.
Therefore, you need to create a `MicrometerChatModelListener` with the available `ObservationRegistry` and `MeterRegistry`.

Then, add the `MicrometerChatModelListener` to a list of listeners for your ChatModel.
Finally, add this list of listeners to the chat model in its builder.

```java
List<ChatModelListener> list;

public ChatApp(ObservationRegistry observationRegistry, CompositeMeterRegistry meterRegistry) {
        this.list = List.of(new MicrometerChatModelListener(meterRegistry, observationRegistry));
    }
    
// For example an AzureOpenAiChatModel
public AzureOpenAiChatModel createChatModel() {
        return AzureOpenAiChatModel.builder()
                // Omitted for brevity
                .listeners(list)
                .build();
    }
```

## Viewing the metrics
You can view the metrics by visiting the `/actuator/metrics` endpoint of your application. For example, if you are running your application on `localhost:8080`, you can visit `http://localhost:8080/actuator/metrics` to view the metrics.

- `langchain4j.chat.model.request`: `/actuator/metrics/langchain4j.chat.model.request`
- `langchain4j.chat.model.error`: `/actuator/metrics/langchain4j.chat.model.error`
- `gen_ai.client.operation.duration`: `/actuator/metrics/gen_ai.client.operation.duration`
- `gen_ai.client.token.usage`: `/actuator/metrics/gen_ai.client.token.usage`

The measurement of tokens for the `gen_ai.client.token.usage` metric is based on the `gen_ai.token.type` tag. The tag can have the following values:
- `output`: The number of tokens used for the output.
- `input`: The number of tokens used for the input.
- `total`: The total number of tokens used for both input and output.

For each tag (output, input or total), you can view the metrics by visiting the following endpoints:
- `output`: `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:output`
- `input`: `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:input`
- `total`: `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:total`

**Be advised**, the endpoint for the `gen_ai.client.token.usage` metric shows the sum of the values of all tags. This value is NOT the total amount of tokens used by the model. Check the tag `total` for the total amount of tokens used by the model. 
