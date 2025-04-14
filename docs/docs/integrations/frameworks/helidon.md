---
sidebar_position: 9
---

# Helidon

[Helidon](https://helidon.io/) provides a LangChain4j integration module that simplifies to build AI-driven applications while leveraging Helidon’s programming model and style.

Helidon’s LangChain4j integration provides following advantages instead of using LangChain4j library directly:

- Integration with Helidon Inject
    - Automatically creates and registers selected LangChain4j components in the Helidon service registry based on configuration.
- Convention Over Configuration 
    - Simplifies configuration by offering sensible defaults, reducing manual setup for common use cases.
- Declarative AI Services
    - Supports LangChain4j’s AI Services within the declarative programming model, allowing for clean, easy-to-manage code structures.
- Integration with CDI
    - Thanks to the Helidon Inject to CDI Bridge, LangChain4j components can be used in CDI environments, including Helidon MP applications.

These features significantly reduce the complexity of incorporating AI into Helidon applications.

For detailed explanation and usage of LangChain4j integration feature, see the [Helidon documentation](https://helidon.io/docs/latest/se/integrations/langchain4j/langchain4j).
