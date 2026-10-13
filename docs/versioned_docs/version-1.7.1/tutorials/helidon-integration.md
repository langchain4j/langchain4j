---
sidebar_position: 29
---

# Helidon Integration

[Helidon](https://helidon.io/) provides a LangChain4j integration module that simplifies building AI-driven applications while leveraging Helidon’s programming model and style.

You can find the detailed explanation and usage of LangChain4j integration feature [here](https://helidon.io/docs/latest/se/integrations/langchain4j/langchain4j).

## Supported versions

Helidon's LangChain4j integration requires Java 21 and Helidon 4.2.

## Examples

We have created several sample applications for you to explore. These samples demonstrate all aspects of using LangChain4j in Helidon applications.

### Coffee Shop Assistant
The Coffee Shop Assistant is a demo application that showcases how to build an AI-powered assistant for a coffee shop. This assistant can answer questions about the menu, provide recommendations, and create orders. It utilizes an embedding store initialized from a JSON file.

Key features:
- Integration with OpenAI chat models
- Utilization of embedding models, an embedding store, an ingestor, and a content retriever
- Helidon Inject for dependency injection
- Embedding store initialization from a JSON file
- Support for callback functions to enhance interactions

Check it out:
- [Coffee Shop Assistant for Helidon SE](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/langchain4j/coffee-shop-assistant-se)
- [Coffee Shop Assistant for Helidon MP](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/langchain4j/coffee-shop-assistant-mp)

### Hands-on Lab

We also offer a Hands-on Lab with step-by-step instructions on how to build the Coffee Shop Assistant:

[HOL: Building AI-Powered Applications with Helidon and LangChain4j](https://github.com/helidon-io/helidon-labs/tree/main/hols/langchain4j)


