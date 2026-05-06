---
sidebar_position: 1
---

# Prompt Repetition

`langchain4j-community-prompt-repetition` is an optional community module that provides ready-made prompt repetition integrations for two LangChain4j integration points:

- AI Services input guardrails
- RAG query transformation

It is inspired by the paper [Prompt Repetition Improves Non-Reasoning LLMs](https://arxiv.org/html/2512.14982v1), which reports gains on a range of non-reasoning workloads. In LangChain4j, the module exposes the core repeated-input transformation through framework-native components and adds conservative defaults for real applications.

The module is experimental, and its effectiveness is workload-dependent. Validate it on your own prompts, models, and tasks before broad rollout.

## What It Is

Prompt repetition rewrites text in the following form:

```text
Q -> Q\nQ
```

In LangChain4j, this can be applied in two different places:

- Before a non-RAG AI Services call, using `PromptRepeatingInputGuardrail`
- Before retrieval in an advanced RAG pipeline, using `RepeatingQueryTransformer`

For RAG, the repetition should be applied only to the retrieval query, not to the final augmented prompt sent to the model.

## Maven Dependencies

If you already use community modules, importing the community BOM is recommended:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-bom</artifactId>
            <version>${latest version here}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the prompt repetition module:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-prompt-repetition</artifactId>
</dependency>
```

You can also declare the module directly:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-prompt-repetition</artifactId>
    <version>${latest version here}</version>
</dependency>
```

## Components

The module provides three main APIs:

- `PromptRepeatingInputGuardrail` repeats eligible single-text user input before the model is called
- `RepeatingQueryTransformer` repeats the retrieval query in advanced RAG pipelines
- `PromptRepetitionPolicy` contains the shared repetition rules used by both integrations

All of these APIs are marked as `@Experimental`.

## Non-RAG Usage

For non-RAG AI Services calls, attach `PromptRepeatingInputGuardrail` to the `AiServices` builder:

```java
PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
        .mode(PromptRepetitionMode.AUTO)
        .maxChars(8_000)
        .build();

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .inputGuardrails(new PromptRepeatingInputGuardrail(policy))
        .build();
```

This is the preferred integration point when you want to rewrite the user input before the model call and you are not operating on an augmented RAG prompt.

## RAG Usage

For RAG, repeat the retrieval query only:

```java
PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
        .mode(PromptRepetitionMode.AUTO)
        .maxChars(8_000)
        .build();

RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryTransformer(new RepeatingQueryTransformer(policy))
        .build();
```

This keeps the transformation inside the retrieval stage and avoids duplicating the final prompt after retrieved content has already been injected.

## Modes

`PromptRepetitionPolicy` supports three modes:

- `NEVER`: disables repetition
- `ALWAYS`: repeats eligible input
- `AUTO`: conservative mode that skips already repeated text, very long input, and prompts that appear to request explicit reasoning

`AUTO` is the safest starting point for evaluation.

## Safety and Constraints

- `PromptRepeatingInputGuardrail` only rewrites eligible single-text user input
- It is not intended to be the main integration point for multimodal requests
- By default, the guardrail skips requests when RAG augmentation has already happened
- In RAG setups, use `RepeatingQueryTransformer` to repeat the retrieval query instead of repeating the final augmented prompt
- The module is experimental, so APIs and behavior may change in future versions

## When to Use It

Use this module when you want a ready-made way to apply prompt repetition in LangChain4j, not when you want a universal default prompt policy.

- Start with `PromptRepetitionMode.AUTO`
- Prefer it for non-reasoning or low-reasoning workloads first
- Evaluate it with A/B tests on your own prompts, models, and tasks
- Keep the default safety constraints unless you have a clear reason to relax them
- Treat improvements as workload-dependent rather than guaranteed
