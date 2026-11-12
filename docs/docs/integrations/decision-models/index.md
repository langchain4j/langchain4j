---
title: Comparison Table of all supported Decision Models
hide_title: false
sidebar_position: 0
---

| Provider                                                            | Module                         | Questions (yes/no, choice, scale) | Async (`decideAsync`) | Input                     | Probabilities and confidence | [Refusals](/tutorials/decision-models#refusals) | [Observability](/tutorials/observability) | [Customizable HTTP Client](/tutorials/customizable-http-client) | Local Deployment                                                                   | [Spring Boot](/tutorials/spring-boot-integration) |
|---------------------------------------------------------------------|--------------------------------|-----------------------------------|-----------------------|---------------------------|------------------------------|-------------------------------------------------|-------------------------------------------|-----------------------------------------------------------------|------------------------------------------------------------------------------------|---------------------------------------------------|
| [Google Gen AI](/integrations/decision-models/google-genai)         | `langchain4j-google-genai`     | ✅/✅/✅                             | ✅                     | text, structured, image, audio, video, PDF, conversation | ✅                            | ✅                                               | ✅                                         |                                                                 | ❌                                                                                  | 🆘                                                 |
| [Ollama](/integrations/decision-models/ollama)                      | `langchain4j-ollama`           | ✅/✅/✅                             | ✅                     | text, structured, image, conversation | ✅                            | ❌                                               | ✅                                         | ✅                                                               | ✅                                                                                  | 🆘                                                 |
| [OpenAI](/integrations/decision-models/open-ai)                     | `langchain4j-open-ai`          | ✅/✅/✅                             | ✅                     | text, structured, image, conversation | ✅                            | ✅                                               | ✅                                         | ✅                                                               | ❌                                                                                  | ✅                                                 |
| [OpenAI Official](/integrations/decision-models/open-ai)            | `langchain4j-open-ai-official` | ✅/✅/✅                             | ✅                     | text, structured, image, conversation | ✅                            | ✅                                               | ✅                                         |                                                                 | ❌                                                                                  | ✅                                                 |
| [TypeSafe](/integrations/decision-models/typesafe)                  | `langchain4j-typesafe`         | ✅/✅/✅                             | ✅                     | text, structured, conversation | ✅                            | ❌                                               | ✅                                         | ✅                                                               | ✅ (any server implementing the System One API, see [here](/integrations/decision-models/typesafe#other-servers)) | 🆘                                                 |

Legend:

- ✅ means "supported"
- 🆘 means "not supported yet; please help us implement it"
- ❌ means "not supported by the provider"
- no mark means "not applicable" (Google Gen AI and OpenAI Official use the HTTP client of their official SDKs, configurable with `client(...)` / `openAIClient(...)`)

Notes:

- "structured" input is named fields (`inputFields(...)`). Google Gen AI and OpenAI send them as text, TypeSafe and Ollama as a JSON
  object.
- "conversation" input is a list of `ChatMessage`s (`inputMessages(...)`), including tool calls and their results,
  sent as a JSON transcript (see [Conversations](/tutorials/decision-models#conversations)).
- Images on Ollama need a model with vision, such as Clef.
- Google Gen AI refuses either the whole input or nothing: when it blocks the input, every question is refused.
- Every implementation reports token usage. OpenAI also reports cached input tokens.
