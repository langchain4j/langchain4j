# Observability with Micrometer
The `langchain4j-micrometer` module provides a Micrometer-based metrics implementation for the `langchain4j` library. For now, it only provides metrics for a chat model interaction.
It uses the Micrometer Observation API in a `ChatModelListener` to collect metrics about the usage of a chat model. The naming of the metrics is based on the [OpenTelemetry Semantic Conventions for Generative AI Metrics](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/). 

> **⚠️ Warning**: The OpenTelemetry Semantic Conventions for Generative AI are currently **experimental and not stable**. This means they may have breaking changes in future versions. If you follow these conventions, you may need to introduce breaking changes to your dashboards, alerts, and automations when the conventions are updated. Consider using the `ObservationConvention` component to support multiple convention versions or define your own custom conventions.

The following metrics are collected:

- `gen_ai.client.operation.duration` - The duration of the GenAI operation with an `outcome` tag (SUCCESS or ERROR).
- `gen_ai.client.token.usage` - The number of tokens used by the model for input or output.

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
The `MicrometerChatModelListener` collects the metrics for the chat model. It uses an `ObservationRegistry` and `MeterRegistry` provided by Micrometer to collect the metrics in an Observation.

**Important**: You must register the `ChatModelMeterObservationHandler` with the `ObservationRegistry` **once** at application startup before creating the listener.

```java
import dev.langchain4j.micrometer.listeners.MicrometerChatModelListener;
import dev.langchain4j.micrometer.observation.ChatModelMeterObservationHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;

// Get the MeterRegistry and ObservationRegistry (from Spring context or create manually)
MeterRegistry meterRegistry = ...; // e.g., new SimpleMeterRegistry() or injected from Spring
ObservationRegistry observationRegistry = ...; // e.g., ObservationRegistry.create() or injected from Spring

// 1. Register the handler ONCE globally (typically at application startup)
observationRegistry.observationConfig()
    .observationHandler(new ChatModelMeterObservationHandler(meterRegistry));

// 2. Create the listener (no need to pass MeterRegistry, it's in the handler)
List<ChatModelListener> listeners = List.of(
    new MicrometerChatModelListener(observationRegistry, "azure_openai")
);

// 3. Add listeners to your ChatModel
// For example an AzureOpenAiChatModel
AzureOpenAiChatModel chatModel = AzureOpenAiChatModel.builder()
    // Omitted for brevity
    .listeners(listeners)
    .build();
```

## Viewing the metrics
You can view the metrics by visiting the `/actuator/metrics` endpoint of your application. For example, if you are running your application on `localhost:8080`, you can visit `http://localhost:8080/actuator/metrics` to view the metrics.

- `gen_ai.client.operation.duration`: `/actuator/metrics/gen_ai.client.operation.duration`
- `gen_ai.client.token.usage`: `/actuator/metrics/gen_ai.client.token.usage`

The `gen_ai.client.operation.duration` metric includes an `outcome` tag with values:
- `SUCCESS`: The operation completed successfully
- `ERROR`: The operation failed with an error

You can filter by outcome:
- Success: `/actuator/metrics/gen_ai.client.operation.duration?tag=outcome:SUCCESS`
- Error: `/actuator/metrics/gen_ai.client.operation.duration?tag=outcome:ERROR`

The measurement of tokens for the `gen_ai.client.token.usage` metric is based on the `gen_ai.token.type` tag. The tag can have the following values:
- `output`: The number of tokens used for the output.
- `input`: The number of tokens used for the input.

For each tag (output, input or total), you can view the metrics by visiting the following endpoints:
- `output`: `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:output`
- `input`: `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:input`

**Note**, the endpoint for the `gen_ai.client.token.usage` metric, without any tags, shows the sum of the values of both the output and the input tags. Subsequently, this value is the total amount of tokens used by the model. 
