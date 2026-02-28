---
sidebar_position: 9
---

# ReasoningBank (Experimental)

ReasoningBank is a memory framework that enables agents to store, retrieve, and reuse
successful reasoning strategies learned from past experiences. Unlike RAG, which retrieves
*what* information to use, ReasoningBank retrieves *how* to approach a task.

:::note
This feature is experimental and may change in future releases.
:::

## What is ReasoningBank?

ReasoningBank implements the concept from the paper "Scaling Agent Self-Evolving with Reasoning Memory"
([arXiv:2509.25140](https://arxiv.org/abs/2509.25140)). The core idea is to capture reasoning patterns
that led to successful outcomes and make them available for similar future tasks.

Key concepts:
- **ReasoningTrace**: Raw data captured during task execution (task description, thinking process, solution, success/failure)
- **ReasoningStrategy**: A distilled, generalizable approach extracted from traces
- **ReasoningBank**: Storage for reasoning strategies with similarity-based retrieval
- **ReasoningAugmentor**: Augments prompts with relevant strategies (similar to RAG's `RetrievalAugmentor`)

## When to Use ReasoningBank

ReasoningBank is useful when:
- You have repeatable task patterns where past approaches can help
- You want the LLM to learn from successful problem-solving strategies
- You want to improve consistency across similar tasks
- You're building agents that need to self-improve over time

## Quick Start

### 1. Add Dependencies

ReasoningBank is included in the core `langchain4j` module:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.11.0</version>
</dependency>
```

### 2. Create a ReasoningBank

```java
import dev.langchain4j.reasoning.InMemoryReasoningBank;
import dev.langchain4j.reasoning.ReasoningBank;

ReasoningBank reasoningBank = new InMemoryReasoningBank();
```

### 3. Store Reasoning Strategies

Strategies can be created manually or distilled from traces:

```java
import dev.langchain4j.reasoning.ReasoningStrategy;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

EmbeddingModel embeddingModel = ... // Your embedding model

// Create a strategy manually
ReasoningStrategy strategy = ReasoningStrategy.builder()
    .taskPattern("mathematical problems")
    .strategy("Break the problem into smaller steps and verify each step")
    .pitfallsToAvoid("Don't skip verification steps")
    .confidenceScore(0.9)
    .build();

// Create embedding for similarity search
Embedding embedding = embeddingModel.embed(strategy.taskPattern()).content();

// Store in the bank
String id = reasoningBank.store(strategy, embedding);
```

### 4. Configure AI Service with ReasoningAugmentor

```java
import dev.langchain4j.reasoning.ReasoningAugmentor;
import dev.langchain4j.service.AiServices;

interface Assistant {
    String chat(String message);
}

ReasoningAugmentor reasoningAugmentor = ReasoningAugmentor.builder()
    .reasoningBank(reasoningBank)
    .embeddingModel(embeddingModel)
    .maxStrategies(3)    // Maximum strategies to include
    .minScore(0.5)       // Minimum similarity score
    .build();

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .reasoningAugmentor(reasoningAugmentor)
    .build();

// When the assistant receives a math problem, relevant strategies are injected
String response = assistant.chat("Solve the equation: x + 5 = 10");
```

## Core Components

### ReasoningTrace

A `ReasoningTrace` captures the raw data from a task execution:

```java
import dev.langchain4j.reasoning.ReasoningTrace;

// For successful task completions
ReasoningTrace successfulTrace = ReasoningTrace.successful(
    "Solve x + 5 = 10",           // task description
    "Subtract 5 from both sides", // thinking process
    "x = 5"                       // solution
);

// For failed attempts
ReasoningTrace failedTrace = ReasoningTrace.failed(
    "Complex integration",        // task description
    "Tried substitution but..."   // what was attempted
);

// With full builder
ReasoningTrace trace = ReasoningTrace.builder()
    .taskDescription("Debug null pointer exception")
    .thinking("First, check the stack trace to identify the line...")
    .solution("Added null check before method call")
    .successful(true)
    .metadata(Metadata.from(Map.of("duration", "30m")))
    .build();
```

### ReasoningStrategy

A `ReasoningStrategy` is a distilled, reusable approach:

```java
import dev.langchain4j.reasoning.ReasoningStrategy;

// Simple creation
ReasoningStrategy simple = ReasoningStrategy.from(
    "debugging tasks",
    "Start by reading the error message and stack trace"
);

// Full builder
ReasoningStrategy strategy = ReasoningStrategy.builder()
    .taskPattern("code review tasks")
    .strategy("Focus on logic errors first, then style issues")
    .pitfallsToAvoid("Don't nitpick formatting before checking correctness")
    .confidenceScore(0.85)
    .metadata(Metadata.from(Map.of("source", "senior_developer")))
    .build();
```

### ReasoningBank

The `ReasoningBank` interface provides storage and retrieval:

```java
import dev.langchain4j.reasoning.ReasoningBank;
import dev.langchain4j.reasoning.ReasoningRetrievalRequest;
import dev.langchain4j.reasoning.ReasoningRetrievalResult;

// Store strategies
String id = reasoningBank.store(strategy, embedding);
List<String> ids = reasoningBank.storeAll(strategies, embeddings);

// Retrieve similar strategies
Embedding queryEmbedding = embeddingModel.embed("help with math").content();
ReasoningRetrievalResult result = reasoningBank.retrieve(queryEmbedding, 5);

// Or with full request options
ReasoningRetrievalRequest request = ReasoningRetrievalRequest.builder()
    .queryEmbedding(queryEmbedding)
    .maxResults(5)
    .minScore(0.6)
    .build();
ReasoningRetrievalResult result = reasoningBank.retrieve(request);

// Access results
for (ReasoningMatch match : result.matches()) {
    System.out.println("Strategy: " + match.strategy().strategy());
    System.out.println("Score: " + match.score());
}

// Manage storage
reasoningBank.remove(id);
reasoningBank.clear();
int size = reasoningBank.size();
boolean empty = reasoningBank.isEmpty();
```

### SimpleReasoningDistiller

The `SimpleReasoningDistiller` transforms traces into strategies:

```java
import dev.langchain4j.reasoning.SimpleReasoningDistiller;

SimpleReasoningDistiller distiller = SimpleReasoningDistiller.builder()
    .baseConfidence(0.7)
    .learnFromFailures(true)
    .build();

// Distill single trace
List<ReasoningStrategy> strategies = distiller.distill(trace);

// Distill multiple traces
List<ReasoningStrategy> strategies = distiller.distillAll(traces);

// Refine existing strategy with new evidence
ReasoningStrategy refined = distiller.refine(existingStrategy, newTraces);
```

### ReasoningAugmentor

The `ReasoningAugmentor` integrates with AI Services to enhance prompts:

```java
import dev.langchain4j.reasoning.ReasoningAugmentor;
import dev.langchain4j.reasoning.ReasoningAugmentationResult;

ReasoningAugmentor augmentor = ReasoningAugmentor.builder()
    .reasoningBank(reasoningBank)
    .embeddingModel(embeddingModel)
    .maxStrategies(3)
    .minScore(0.5)
    .injector(customInjector)  // Optional custom injector
    .build();

// Manual augmentation (usually done automatically by AI Services)
UserMessage userMessage = UserMessage.from("Solve this problem...");
ReasoningAugmentationResult result = augmentor.augment(userMessage);

ChatMessage augmentedMessage = result.augmentedMessage();
List<ReasoningStrategy> usedStrategies = result.retrievedStrategies();
boolean wasAugmented = result.wasAugmented();
```

## How It Works with AI Services

When you configure an AI Service with a `ReasoningAugmentor`:

1. User sends a message
2. `ReasoningAugmentor` embeds the message and searches the `ReasoningBank`
3. Matching strategies are retrieved and injected into the prompt
4. The augmented message is sent to the LLM

The injection happens *before* RAG augmentation (if configured), because:
- Reasoning strategies tell the LLM *how* to approach the problem
- RAG provides *what* information to use
- This order ensures strategies guide how the LLM uses the retrieved content

## Combining with RAG

ReasoningBank and RAG work together naturally:

```java
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .reasoningAugmentor(reasoningAugmentor)  // HOW to approach
    .contentRetriever(contentRetriever)       // WHAT information to use
    .build();
```

When both are configured:
1. `ReasoningAugmentor` injects strategies for *how* to approach the task
2. RAG injects content for *what* information to use
3. The LLM applies the strategies while using the provided information

## Custom Strategy Injection

You can customize how strategies are injected:

```java
import dev.langchain4j.reasoning.ReasoningAugmentor.ReasoningInjector;

ReasoningInjector customInjector = (strategies, userMessage) -> {
    String strategiesText = strategies.toPromptText();
    String original = userMessage.singleText();

    String augmented = "Consider these approaches:\n" + strategiesText
        + "\n\nTask:\n" + original;

    return UserMessage.from(augmented);
};

ReasoningAugmentor augmentor = ReasoningAugmentor.builder()
    .reasoningBank(reasoningBank)
    .embeddingModel(embeddingModel)
    .injector(customInjector)
    .build();
```

## In-Memory Implementation

`InMemoryReasoningBank` is suitable for development, testing, and moderate-scale usage:

```java
InMemoryReasoningBank bank = new InMemoryReasoningBank();

// Or with builder
InMemoryReasoningBank bank = InMemoryReasoningBank.builder()
    .initialEntries(existingEntries)
    .build();

// Access entries for debugging
List<InMemoryReasoningBank.Entry> entries = bank.entries();
for (InMemoryReasoningBank.Entry entry : entries) {
    System.out.println("ID: " + entry.id());
    System.out.println("Strategy: " + entry.strategy());
}
```

Features:
- Thread-safe using `CopyOnWriteArrayList`
- Cosine similarity search with `RelevanceScore` conversion
- Combines similarity score with strategy confidence score
- Priority queue for efficient top-K retrieval

## Complete Example

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.reasoning.*;
import dev.langchain4j.service.AiServices;

public class ReasoningBankExample {

    interface MathAssistant {
        String solve(String problem);
    }

    public static void main(String[] args) {
        // Setup models
        ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4o-mini")
            .build();

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();

        // Create reasoning bank and populate with strategies
        InMemoryReasoningBank reasoningBank = new InMemoryReasoningBank();

        ReasoningStrategy algebraStrategy = ReasoningStrategy.builder()
            .taskPattern("algebraic equations")
            .strategy("Isolate the variable by performing inverse operations on both sides")
            .pitfallsToAvoid("Don't forget to apply operations to both sides equally")
            .confidenceScore(0.9)
            .build();

        Embedding embedding = embeddingModel.embed("algebraic equations").content();
        reasoningBank.store(algebraStrategy, embedding);

        // Create augmentor
        ReasoningAugmentor augmentor = ReasoningAugmentor.builder()
            .reasoningBank(reasoningBank)
            .embeddingModel(embeddingModel)
            .maxStrategies(2)
            .build();

        // Create AI service
        MathAssistant assistant = AiServices.builder(MathAssistant.class)
            .chatModel(chatModel)
            .reasoningAugmentor(augmentor)
            .build();

        // Use it
        String solution = assistant.solve("Solve for x: 3x + 7 = 22");
        System.out.println(solution);
    }
}
```

## Learning from Experience

A complete workflow for self-improving agents:

```java
// 1. Execute task and capture trace
ReasoningTrace trace = ReasoningTrace.builder()
    .taskDescription(userTask)
    .thinking(llmThinkingProcess)
    .solution(llmSolution)
    .successful(wasSuccessful)
    .build();

// 2. Distill successful traces into strategies
SimpleReasoningDistiller distiller = new SimpleReasoningDistiller();
List<ReasoningStrategy> newStrategies = distiller.distill(trace);

// 3. Store in the bank
for (ReasoningStrategy strategy : newStrategies) {
    Embedding embedding = embeddingModel.embed(strategy.taskPattern()).content();
    reasoningBank.store(strategy, embedding);
}

// 4. Future tasks benefit from learned strategies
// (automatic through ReasoningAugmentor)
```

## Best Practices

1. **Use descriptive task patterns**: The `taskPattern` is used for similarity matching, so make it descriptive
2. **Include pitfalls to avoid**: Learning from failures is as important as learning from successes
3. **Set appropriate confidence scores**: Higher scores for well-validated strategies
4. **Tune maxStrategies**: Too many strategies can overwhelm the context; start with 2-3
5. **Set minScore threshold**: Filter out low-relevance strategies to reduce noise
6. **Combine with RAG thoughtfully**: Reasoning strategies guide approach; RAG provides content

## Limitations

- **In-memory only**: Current implementation stores strategies in memory. For persistence, you'll need to implement your own `ReasoningBank` backed by a database or embedding store.
- **Simple distillation**: `SimpleReasoningDistiller` uses basic heuristics. For more sophisticated distillation, consider using an LLM to summarize and generalize strategies.
- **No automatic learning**: Currently, you must manually capture traces and store strategies. Future versions may include automatic trace capture.
