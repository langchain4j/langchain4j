---
title: OpenTelemetry GenAI instrumentation
sidebar_position: 45
---

The community project [otel-genai-bridges](https://github.com/dineshkumarkummara/otel-genai-bridges) provides a Spring Boot starter that auto-instruments LangChain4j chat applications using the [OpenTelemetry Generative AI semantic conventions](https://github.com/open-telemetry/semantic-conventions/tree/main/docs/gen-ai).

## Why use it?

- `com.dineshkumarkummara.otel:langchain4j-otel` wraps any `ChatLanguageModel` bean and emits spans, events, and metrics.
- Prompts, completions, tool calls, latency, token usage, cost, and RAG retrieval latency are captured out of the box.
- Samples ship with a Docker Compose stack (Collector → Tempo/Prometheus → Grafana) and Grafana dashboards ready to go.

## Getting started

Add the starter to your Spring Boot project:

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.dineshkumarkummara.otel</groupId>
  <artifactId>langchain4j-otel</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure the instrumentation via `application.yaml`:

```yaml
otel:
  langchain4j:
    enabled: true
    system: openai
    default-model: gpt-4o
    capture-prompts: true
    capture-completions: true
```

The starter detects any `ChatLanguageModel` beans and automatically wraps them with telemetry.

## Observability view

![Grafana latency panel](https://github.com/dineshkumarkummara/otel-genai-bridges/raw/main/docs/screenshots/grafana-latency.png)

For a full example (including Dockerized observability and Semantic Kernel parity), see the repository: [dineshkumarkummara/otel-genai-bridges](https://github.com/dineshkumarkummara/otel-genai-bridges).
