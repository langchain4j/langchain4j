---
sidebar_position: 7
---

# Agents

:::note
Please note that "Agent" is a very broad term with multiple definitions.
:::

## Recommended Reading

- [Building effective agents](https://www.anthropic.com/research/building-effective-agents) by Anthropic

## Agent

Most of the basic "agentic" functionality can be built using a high-level [AI Service](/tutorials/ai-services)
and [Tool](/tutorials/tools#high-level-tool-api) APIs.

If you need more flexibility, you can use the low-level
[ChatModel](/tutorials/chat-and-language-models),
[ToolSpecification](/tutorials/tools#low-level-tool-api)
and [ChatMemory](/tutorials/chat-memory) APIs.

## Multi-Agent

LangChain4j does not support _high-level_ abstractions like "agent" in
[AutoGen](https://github.com/microsoft/autogen)
or [CrewAI](https://www.crewai.com/) to build multi-agent systems.

However, you can still build multi-agent systems by using the low-level
[ChatModel](/tutorials/chat-and-language-models),
[ToolSpecification](/tutorials/tools#low-level-tool-api)
and [ChatMemory](/tutorials/chat-memory) APIs.

## Examples

- [Customer Support Agent](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/test/java/dev/langchain4j/example/CustomerSupportAgentIT.java)
