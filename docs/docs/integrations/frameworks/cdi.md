---
sidebar_position: 9
---

# LangChain4J CDI

[LangChain4J CDI](https://github.com/langchain4j/langchain4j-cdi)  injects AI services directly into your Jakarta EE and MicroProfile applications.

## Documentation

Full documentation is available at **[langchain4j.github.io/langchain4j-cdi](https://langchain4j.github.io/langchain4j-cdi/)**.

## Features

- **AI Service Injection** — declare AI services as CDI beans using `@RegisterAIService`
- **Agentic Topologies** — 11 per-topology annotations (`@RegisterSimpleAgent`, `@RegisterSequenceAgent`, `@RegisterLoopAgent`, etc.) for multi-agent workflows
- **MCP Server** — expose CDI beans as a Model Context Protocol server
- **Configuration via Properties** — configure LLM components through MicroProfile Config or a custom SPI
- **Fault Tolerance** — resilience with `@Retry`, `@Timeout`, `@CircuitBreaker`, `@Fallback`
- **Telemetry** — OpenTelemetry-based observability for AI operations
- **Expression Language** — resolve `${...}` (MicroProfile Config) and `#{...}` (Jakarta EL) expressions in annotations
- **Guardrails** — input and output validation for AI service interactions

## Supported Runtimes

| Runtime | Extension Type |
|---------|---------------|
| Quarkus | Build-compatible |
| Helidon | Both |
| WildFly | Portable |
| Payara | Portable |
| GlassFish | Portable |
| Liberty | Portable |

For detailed explanation and usage of LangChain4j CDI features, see the [LangChain4J CDI docuemntation](https://langchain4j.github.io/langchain4j-cdi/).
