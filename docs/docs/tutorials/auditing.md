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

| Event Name                                                                                                                                                                                          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`AiServiceInteractionStartedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionStartedEvent.java)     | Invoked when an LLM interaction has started.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| [`AiServiceResponseReceivedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceResponseReceivedEvent.java)         | Invoked with a response from an LLM. It is important to note that this can be invoked multiple times during a single AiService interaction when tools or guardrails exist.<br/><br/> Contains information such as the system message and the user message.<br/><br/>Not every interaction will receive this event. If an interaction fails it will receive an [`AiServiceInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionErrorEvent.java) instead. |
| [`AiServiceInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionErrorEvent.java)         | Fired when an interaction with an LLM fails. The failure could be because of network failure, AiService unavailable, input/output guardrails blocking the request, or many other reasons.<br/><br/>Contains information about the failure that occurred.                                                                                                                                                                                                                                                                                                           |
| [`AiServiceInteractionCompletedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionCompletedEvent.java) | Invoked when an LLM interaction has completed successfully.<br/><br/>Not every interaction will receive this event. If an interaction fails it will receive an [`AiServiceInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionErrorEvent.java) instead.<br/><br/>Contains information about the result of the interaction.                                                                                                                             |
| [`ToolExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/ToolExecutedEvent.java)                                   | Invoked when a tool invocation has completed. It is important to note that this can be invoked multiple times within a single llm interaction.<br/><br/>Contains information about the tool request and result.                                                                                                                                                                                                                                                                                                                                                    |
| [`InputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/InputGuardrailExecutedEvent.java)               | Invoked when an [input guardrail](https://docs.langchain4j.dev/tutorials/guardrails#input-guardrails) validation has been executed. One of these events will be fired for each invocation of a guardrail.<br/><br/>Contains information about the input to an individual input guardrail as well as its output (i.e. was it successful or a failure?).                                                                                                                                                                                                             |
| [`OutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/OutputGuardrailExecutedEvent.java)             | Invoked when an [output guardrail](https://docs.langchain4j.dev/tutorials/guardrails#output-guardrails) validation has been executed. One of these events will be fired for each invocation of a guardrail.<br/><br/>Contains information about the input to an individual output guardrail as well as its output (i.e. was it successful? failure? a retry? reprompt?).                                                                                                                                                                                           |

## Listening for an event

Each of the [types of events](#types-of-events) has its own listener that can be implemented to receive the event. You can pick and choose which events you want to listen for.

To listen for an event, create your own class implementing the listener interface you'd like to listen to. These are the available listener interfaces:

| Listener Name                                                                                                                                                                                                          | Event                                                                                                                                                                                               |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`AiServiceInteractionStartedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/AiServiceInteractionStartedEventListener.java)     | [`AiServiceInteractionStartedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionStartedEvent.java)     |
| [`AiServiceResponseReceivedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/AiServiceResponseReceivedEventListener.java)         | [`AiServiceResponseReceivedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceResponseReceivedEvent.java)         |
| [`AiServiceInteractionErrorEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/AiServiceInteractionErrorEventListener.java)         | [`AiServiceInteractionErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionErrorEvent.java)         |
| [`AiServiceInteractionCompletedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/AiServiceInteractionCompletedEventListener.java) | [`AiServiceInteractionCompletedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionCompletedEvent.java) |
| [`ToolExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/ToolExecutedEventListener.java)                                   | [`ToolExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/ToolExecutedEvent.java)                                   |
| [`InputGuardrailExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/InputGuardrailExecutedEventListener.java)               | [`InputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/InputGuardrailExecutedEvent.java)               |
| [`OutputGuardrailExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/OutputGuardrailExecutedEventListener.java)             | [`OutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/OutputGuardrailExecutedEvent.java)             |

Once you've defined your listener(s), register them with the [`AiServiceInteractionEventListenerRegistrar`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/AiServiceInteractionEventListenerRegistrar.java).

For example, you could do the following to create and register a listener for an `AiServiceInteractionCompletedEvent`:

```java
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.langchain4j.audit.api.AiServiceInteractionEventListenerRegistrar;
import dev.langchain4j.audit.api.event.InteractionSource;
import dev.langchain4j.audit.api.event.AiServiceInvocationCompletedEvent;
import dev.langchain4j.audit.api.listener.AiServiceInteractionCompletedEventListener;

public class MyAiServiceInteractionCompletedEventListener implements AiServiceInteractionCompletedEventListener {
    @Override
    public void onEvent(AiServiceInteractionCompletedEvent event) {
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

MyAiServiceInteractionCompletedEventListener myListener = new MyAiServiceInteractionCompletedEventListener();
AiServiceInteractionEventListenerRegistrar.getInstance().register(myListener);
```

## Creating your own events and listeners

The auditing capabilities are designed to be extensible. If you'd like to create your own events, you can do so by implementing the [`AiServiceInteractionEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/event/AiServiceInteractionEvent.java) interface to define your own event.

Then, create your own event listener by implementing the [`AiServiceInteractionEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/listener/AiServiceInteractionEventListener.java) interface.

Once you have your event and listener, you need to fire the event by calling `AiServiceInteractionEventListenerRegistrar.getInstance().fireEvent(event)`.

Once the event is getting fired, you can then create listeners and register your listeners just like you would with the built-in events.

## Extension points

You can also create your own custom [`AiServiceInteractionEventListenerRegistrar`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/audit/api/AiServiceInteractionEventListenerRegistrar.java) by implementing the [`AiServiceInteractionEventListenerRegistrarFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/audit/AiServiceInteractionEventListenerRegistrarFactory.java) and registering it with the [Java Service Provider Interface (Java SPI)](https://www.baeldung.com/java-spi).

This could be useful if you want to manage the way you register/unregister your listeners and/or how you want to fire your events.
