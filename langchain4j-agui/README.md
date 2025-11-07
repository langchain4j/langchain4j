# LangChain4j AG-UI Integration

A LangChain4j integration that implements AG-UI agents, enabling you to build AI-powered conversational agents with streaming support, tool execution, and memory management using the LangChain4j framework.

## Overview
This module provides LangchainAgent, a concrete implementation of AG-UI's LocalAgent that bridges AG-UI's agent orchestration with LangChain4j's AI capabilities. It allows you to create conversational AI assistants that can handle real-time streaming responses, execute tools dynamically, and maintain conversation history—all while conforming to the AG-UI specification.

## What is AG-UI?
AG-UI is an open standard for building agentic user interfaces. It provides a framework for creating AI agents with a consistent event-driven architecture, tool execution patterns, and state management. This implementation brings AG-UI to the Java ecosystem via LangChain4j.

## Key Features

* AG-UI Compliant: Full implementation of AG-UI's LocalAgent specification
* Streaming & Non-Streaming Support: Works with both StreamingChatModel and ChatModel implementations
* Tool Execution: Supports LangChain4j tools with automatic mapping to AG-UI's tool format
* Memory Management: Integrates with LangChain4j's ChatMemory for conversation persistence
* Real-time Events: Emits AG-UI standard events for streaming tokens, tool calls, and execution status
* Flexible Configuration: Builder pattern for easy agent configuration
* Hallucination Handling: Custom strategy for handling non-existent tool calls
* State Management: Maintains agent state across conversation turns using AG-UI's State

## Installation
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agui</artifactId>
    <version>1.9.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Basic Usage
```java
// Create a simple agent with streaming
LangchainAgent agent = LangchainAgent.builder()
    .agentId("my-assistant")
    .streamingChatModel(streamingChatModel)
    .systemMessage("You are a helpful assistant.")
    .build();
```
### With Tools

```java
// Create an agent with custom tools
LangchainAgent agent = LangchainAgent.builder()
    .agentId("tool-agent")
    .streamingChatModel(streamingChatModel)
    .tool(new WeatherTool())
    .tool(new CalculatorTool())
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .build();
```

### With Dynamic System Messages

```java
// Create an agent with dynamic system message
LangchainAgent agent = LangchainAgent.builder()
    .agentId("context-aware-agent")
    .streamingChatModel(streamingChatModel)
    .systemMessageProvider(localAgent ->
        "You are assisting with: " + localAgent.getState().get("task")
    )
    .state(new State(Map.of("task", "code review")))
    .build();
```

## Event System

The agent emits events throughout the conversation lifecycle:

- `runStartedEvent`: When agent execution begins
- `textMessageStartEvent`: When response generation starts
- `textMessageContentEvent`: For each streaming token
- `textMessageEndEvent`: When response generation completes
- `toolCallStartEvent`: When a tool execution begins
- `toolCallArgsEvent`: When tool arguments are processed
- `toolCallEndEvent`: When a tool execution completes
- `runFinishedEvent`: When agent execution completes
- `runErrorEvent`: When an error occurs

## Architecture
```
LangchainAgent (implements AG-UI LocalAgent)
    │
    ├── StreamingChatModel / ChatModel
    │   └── Any LangChain4j model (OpenAI, Anthropic, etc.)
    │
    ├── ChatMemory
    │   └── MessageWindowChatMemory, etc.
    │
    ├── Tools / ToolProvider
    │   ├── @Tool annotated methods
    │   └── ToolMapper (AG-UI ↔ LangChain4j conversion)
    │
    └── Assistant (internal AiServices proxy)
        └── TokenStream (streaming response handler)
```

## Testing

Run tests with Maven:

```bash 
mvn test
```

## Contributing
Contributions are welcome! This is part of the LangChain4j project.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License
This project follows the LangChain4j license.

## Author
[Pascal Wilbrink](https://github.com/pascalwilbrink)

## Related Projects

[AG-UI](https://docs.ag-ui.com) - The AG-UI framework for Java
[AG-UI Repository](https://github.com/ag-ui-protocol/ag-ui) - The AG-UI Repository

## Support

LangChain4j Documentation: https://docs.langchain4j.dev
GitHub Issues: langchain4j/langchain4j
