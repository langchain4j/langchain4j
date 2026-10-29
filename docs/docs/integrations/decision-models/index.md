---
title: Comparison Table of all supported Decision Models
hide_title: false
sidebar_position: 0
---

| Provider                                                            | Module                         | Questions (yes/no, choice, scale) | Async (`decideAsync`) | Input                     | Probabilities and confidence | [Refusals](/tutorials/decision-models#refusals) | [Observability](/tutorials/observability) | [Customizable HTTP Client](/tutorials/customizable-http-client) | Local Deployment                                                                   | [Spring Boot](/tutorials/spring-boot-integration) |
|---------------------------------------------------------------------|--------------------------------|-----------------------------------|-----------------------|---------------------------|------------------------------|-------------------------------------------------|-------------------------------------------|-----------------------------------------------------------------|------------------------------------------------------------------------------------|---------------------------------------------------|
| [Ollama](/integrations/decision-models/ollama)                      | `langchain4j-ollama`           | ✅/✅/✅                             | ✅                     | text, structured, image, conversation | ✅                            | ❌                                               | ✅                                         | ✅                                                               | ✅                                                                                  | 🆘                                                 |
| [OpenAI](/integrations/decision-models/open-ai)                     | `langchain4j-open-ai`          | ✅/✅/✅                             | ✅                     | text, structured, image, conversation | ✅                            | ✅                                               | ✅                                         | ✅                                                               | ❌                                                                                  | ✅                                                 |
| [OpenAI Official](/integrations/decision-models/open-ai)            | `langchain4j-open-ai-official` | ✅/✅/✅                             | ✅                     | text, structured, image, conversation | ✅                            | ✅                                               | ✅                                         |                                                                 | ❌                                                                                  | ✅                                                 |
| [TypeSafe](/integrations/decision-models/typesafe)                  | `langchain4j-typesafe`         | ✅/✅/✅                             | ✅                     | text, structured, conversation | ✅                            | ❌                                               | ✅                                         | ✅                                                               | ✅ (any server implementing the System One API, see [here](/integrations/decision-models/typesafe#other-servers)) | 🆘                                                 |

Legend:

- ✅ means "supported"
- 🆘 means "not supported yet; please help us implement it"
- ❌ means "not supported by the provider"
- no mark means "not applicable" (OpenAI Official uses the HTTP client of the official OpenAI SDK, configurable with `openAIClient(...)`)

Notes:

- "structured" input is named fields (`inputFields(...)`). OpenAI sends them as text, TypeSafe and Ollama as a JSON
  object.
- "conversation" input is a list of `ChatMessage`s (`inputMessages(...)`), including tool calls and their results,
  sent as a JSON transcript (see [Conversations](/tutorials/decision-models#conversations)).
- Images on Ollama need a model with vision, such as Clef.
- Every implementation reports token usage. OpenAI also reports cached input tokens.
