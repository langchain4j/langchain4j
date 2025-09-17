---
sidebar_position: 13
toc_max_heading_level: 5
---

# Auditing

:::note
Auditing is an experimental feature. Its API and behavior may change in future versions.
:::

Auditing mechanisms allow users to track what is happening during an `AiService` interaction. A single interaction may involve multiple LLM interactions, any of which may succeed or fail. Auditing allows users to track the full sequence of interactions and their outcomes.

:::note
The auditing capabilities are only available when using [AI Services](/tutorials/ai-services). They are a higher-level construct that can not be applied to a `ChatModel` or `StreamingChatModel`.
:::

The implementation was originally implemented in the [Quarkus LangChain4j extension](https://docs.quarkiverse.io/quarkus-langchain4j/dev/) and was backported here.

## Types of events

Each type of event has a unique identifier, which can be used to correlate events across multiple interactions. Each type of event includes information encapsulated inside an [`InteractionSource`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/InteractionSource.java).

The following types of events are currently available:

| Event Name                                                                                                                                                                              | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`LLMInteractionStartedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionStartedEvent.java)     | Invoked when an LLM interaction has started.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| [`LLMResponseReceivedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMResponseReceivedEvent.java)         | Invoked with a response from an LLM. It is important to note that this can be invoked multiple times during a single LLM interaction when tools or guardrails exist.<br/><br/> Contains information such as the system message and the user message.<br/><br/>Not every interaction will receive this event. If an interaction fails it will receive an [`LLMInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionErrorEvent.java) instead. |
| [`LLMInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionErrorEvent.java)         | Fired when an interaction with an LLM fails. The failure could be because of network failure, LLM unavailable, input/output guardrails blocking the request, or many other reasons.<br/><br/>Contains information about the failure that occurred.                                                                                                                                                                                                                                                                                               |
| [`LLMInteractionCompletedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionCompletedEvent.java) | Invoked when an LLM interaction has completed successfully.<br/><br/>Not every interaction will receive this event. If an interaction fails it will receive an [`LLMInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionErrorEvent.java) instead.<br/><br/>Contains information about the result of the interaction.                                                                                                                       |
| [`ToolExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/ToolExecutedEvent.java)                       | Invoked when a tool invocation has completed. It is important to note that this can be invoked multiple times within a single llm interaction.<br/><br/>Contains information about the tool request and result.                                                                                                                                                                                                                                                                                                                                  |
| [`InputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/InputGuardrailExecutedEvent.java)   | Invoked when an [input guardrail](https://docs.langchain4j.dev/tutorials/guardrails#input-guardrails) validation has been executed. One of these events will be fired for each invocation of a guardrail.<br/><br/>Contains information about the input to an individual input guardrail as well as its output (i.e. was it successful or a failure?).                                                                                                                                                                                           |
| [`OutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/OutputGuardrailExecutedEvent.java) | Invoked when an [output guardrail](https://docs.langchain4j.dev/tutorials/guardrails#output-guardrails) validation has been executed. One of these events will be fired for each invocation of a guardrail.<br/><br/>Contains information about the input to an individual output guardrail as well as its output (i.e. was it successful? failure? a retry? reprompt?).                                                                                                                                                                         |

## Listening for an event

Each of the [types of events](#types-of-events) has its own listener that can be implemented to receive the event. You can pick and choose which events you want to listen for.

To listen for an event, create your own class implementing the listener interface you'd like to listen to. These are the available listener interfaces:

| Listener Name                                                                                                                                                                                              | Event                                                                                                                                                                                   |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`LLMInteractionStartedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/LLMInteractionStartedEventListener.java)     | [`LLMInteractionStartedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionStartedEvent.java)     |
| [`LLMResponseReceivedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/LLMResponseReceivedEventListener.java)         | [`LLMResponseReceivedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMResponseReceivedEvent.java)         |
| [`LLMInteractionErrorEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/LLMInteractionErrorEventListener.java)         | [`LLMInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionErrorEvent.java)         |
| [`LLMInteractionCompletedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/LLMInteractionCompletedEventListener.java) | [`LLMInteractionCompletedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionCompletedEvent.java) |
| [`ToolExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/ToolExecutedEventListener.java)                       | [`ToolExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/ToolExecutedEvent.java)                       |
| [`InputGuardrailExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/InputGuardrailExecutedEventListener.java)   | [`InputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/InputGuardrailExecutedEvent.java)   |
| [`OutputGuardrailExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/OutputGuardrailExecutedEventListener.java) | [`OutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/OutputGuardrailExecutedEvent.java) |

Once you've defined your listener(s), register them with the [`LLMInteractionEventListenerRegistrar`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/LLMInteractionEventListenerRegistrar.java).

For example, you could do the following to create and register a listener for an `LLMInteractionCompletedEvent`:

```java
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.langchain4j.audit.api.LLMInteractionEventListenerRegistrar;
import dev.langchain4j.audit.api.event.InteractionSource;
import dev.langchain4j.audit.api.event.LLMInteractionCompletedEvent;
import dev.langchain4j.audit.api.listener.LLMInteractionCompletedEventListener;

public class MyLLMInteractionCompletedEventListener implements LLMInteractionCompletedEventListener {
    @Override
    public void onEvent(LLMInteractionCompletedEvent event) {
        InteractionSource interactionSource = event.interactionSource();
        Optional<Object> result = event.result();
        
        // The interactionId will be the same for all events related to the same LLM interaction
        UUID interactionId = interactionSource.interactionId();
        String aiServiceInterfaceName = interactionSource.interfaceName();
        String aiServiceMethodName = interactionSource.methodName();
        List<Object> methodArgs = interactionSource.methodArguments();
        Optional<Object> memoryId = interactionSource.memoryId();
        Instant eventTimestamp = interactionSource.timestamp();
        
        // Do something with the data
        
    }
}

MyLLMInteractionCompletedEventListener myListener = new MyLLMInteractionCompletedEventListener();
LLMInteractionEventListenerRegistrar.getInstance().register(myListener);
```

## Creating your own events and listeners

The auditing capabilities are designed to be extensible. If you'd like to create your own events, you can do so by implementing the [`LLMInteractionEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/LLMInteractionEvent.java) interface to define your own event.

Then, create your own event listener by implementing the [`LLMInteractionEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/LLMInteractionEventListener.java) interface.

Once you have your event and listener, you need to fire the event by calling `LLMInteractionEventListenerRegistrar.getInstance().fireEvent(event)`.

Once the event is getting fired, you can then create listeners and register your listeners just like you would with the built-in events.

## Extension points

You can also create your own custom [`LLMInteractionEventListenerRegistrar`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/LLMInteractionEventListenerRegistrar.java) by implementing the [`LLMInteractionEventListenerRegistrarFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/audit/LLMInteractionEventListenerRegistrarFactory.java) and registering it with the [Java Service Provider Interface (Java SPI)](https://www.baeldung.com/java-spi).

This could be useful if you want to manage the way you register/unregister your listeners and/or how you want to fire your events.
