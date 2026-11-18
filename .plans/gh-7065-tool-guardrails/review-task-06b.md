# Review: #7065 Task 06b (Annotations, Config, and Per-Tool Guardrail Resolution)

**Date:** 2026-11-17

---

## 1. Summary

Overall, the implementation and its rework on top of Task 06a are clean, well-structured, and closely follow the decisions in [task-06b-annotations-and-guardrail-resolution.md](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/.tasks/gh-7065-tool-guardrails/task-06b-annotations-and-guardrail-resolution.md):

- **Hierarchy & Sealing:** [`ToolGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailService.java) (`non-sealed`) and [`ToolGuardrailService.Builder`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailService.java#L130-L141) (`non-sealed`) extend [`GenericGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/GenericGuardrailService.java) and [`GenericGuardrailService.Builder`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/GenericGuardrailService.java#L136-L148); [`DefaultToolGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/DefaultToolGuardrailService.java) (`final`, package-private) and [`ToolGuardrailServiceBuilder`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailServiceBuilder.java) (`final`, package-private) extend [`AbstractKeyedGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/AbstractKeyedGuardrailService.java) and [`AbstractKeyedGuardrailServiceBuilder`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/AbstractKeyedGuardrailServiceBuilder.java), with all `permits` clauses and hierarchy tests updated.
- **Per-Tool Resolution & Memory Safety:** Splitting [`ToolGuardrailChainResolver`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailChainResolver.java) out of [`DefaultToolGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/DefaultToolGuardrailService.java) and storing resolved executors inside [`DefaultToolKey`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/DefaultToolKey.java) via [`KeyedGuardrailExecutors.withLookUp(...)`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/KeyedGuardrailExecutors.java#L80-L83) avoids unbounded map growth for dynamic `ToolProvider` / MCP tools while keeping static tools bound once.
- **SPI & Metadata Lookup:** [`ToolGuardrailServiceBuilderFactory`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/spi/ToolGuardrailServiceBuilderFactory.java) works via `ServiceLoader`, and [`ToolGuardrailChainResolver.findMethodAnnotation`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailChainResolver.java#L180-L184) uses `ClassMetadataProviderFactory.resolveMethodKey(method)` with a fallback to `Method.getAnnotation(...)` for non-public `@Tool` methods.

---

## 2. Must-Fix Issue: Spotless Failure in `langchain4j`

Running `./mvnw -o -Pspotless spotless:check -pl langchain4j,langchain4j-agentic` **fails** on [`ToolGuardrailBinding.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/ToolGuardrailBinding.java#L5-L9):

- `import java.util.Objects;` is placed at line 5 before `import dev.langchain4j.*`, violating the repo’s import-order rule (`dev.*` before `java.*`).
- Run `./mvnw -Pspotless spotless:apply -pl langchain4j` (or move `import java.util.Objects;` after `dev.langchain4j.service.guardrail.ToolGuardrailService.ToolKey`) before committing.

---

## 3. Design & Implementation Findings

### A. Zero-Guardrail Fast Path vs. `ToolServiceContext#toolGuardrailBindings()` Javadoc

- In [`DefaultAiServices.build()`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java#L200), `context.toolService.toolGuardrailService(context.toolGuardrailService())` is **always** called with a non-null [`DefaultToolGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/DefaultToolGuardrailService.java).
- Because [`ToolGuardrailService.bind(Class, AiServiceTool)`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailService.java#L90) always returns a non-null `ToolKey` (with empty `Optional` executors when a tool has no guardrails), [`ToolService.bindTool(...)`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/ToolService.java#L229-L237) populates an entry in `toolGuardrailBindings` for **every** tool of any AI service built through `AiServices`, even when zero tool guardrails are configured anywhere.
- However, the Javadoc on [`ToolServiceContext.toolGuardrailBindings()`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/ToolServiceContext.java#L127-L133) states:
  > *"Returns the guardrail binding (tool source, and the key to execute the tool guardrails of the tool with) of each tool, by tool name. A tool without an entry has no tool guardrails executed, which is the case of every tool when no `ToolGuardrailService` is set."*
- **Why this matters for Tasks 07/08:** A tool **with** an entry in `toolGuardrailBindings` can also have no guardrails (`hasInputGuardrails(key) == false` and `hasOutputGuardrails(key) == false`). Tasks 07 and 08 must check `binding.service().hasInputGuardrails(binding.key())` / `hasOutputGuardrails(binding.key())` (or `ToolGuardrailBinding` could expose convenience `hasInputGuardrails()` / `hasOutputGuardrails()` helpers) **before** building `ToolInputGuardrailRequest` / `ToolOutputGuardrailRequest` to keep the zero-guardrails execution path allocation-free. Updating the Javadoc on `ToolServiceContext.toolGuardrailBindings()` to note this will prevent confusion in Tasks 07/08.

### B. Generic Signatures on `AgentBuilder` Setters

- In [`AiServices.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java#L1373-L1531) and on the existing AI service guardrail setters of [`AgentBuilder.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java#L664-L709), the varargs setters are parameterized with `<I extends ...>` / `<O extends ...>`:
  ```java
  public <I extends InputGuardrail> B inputGuardrailClasses(Class<? extends I>... inputGuardrailClasses)
  public <I extends InputGuardrail> B inputGuardrails(I... inputGuardrails)
  ```
- In [`AgentBuilder.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java#L749-L803), the four new tool guardrail setters omit `<I extends ToolInputGuardrail>` and `<O extends ToolOutputGuardrail>`:
  ```java
  public B toolInputGuardrailClasses(Class<? extends ToolInputGuardrail>... toolInputGuardrailClasses)
  public B toolInputGuardrails(ToolInputGuardrail... toolInputGuardrails)
  ```
  As seen in [`AgentToolGuardrailsTest.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/test/java/dev/langchain4j/agentic/AgentToolGuardrailsTest.java#L164), callers passing a single `GlobalInput.class` to `toolInputGuardrailClasses(...)` get an unchecked generic array creation warning unless `@SuppressWarnings("unchecked")` is added or the method uses `<I extends ToolInputGuardrail>`.

### C. Missing Unit Test Cases from Task 06b Specification

In [`ToolGuardrailResolutionTest.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/test/java/dev/langchain4j/service/guardrail/ToolGuardrailResolutionTest.java):

1. **Method annotation + `AiServiceTool` guardrails on the same tool (Task 06b item 6):**
   - The implementation in [`ToolGuardrailChainResolver.ownGuardrails(...)`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailChainResolver.java#L151-L162) concatenates `fromAnnotations` (class, then method) followed by `tool.tool*Guardrails()`, which is right, but there is no test in `ToolGuardrailResolutionTest` where an `AiServiceTool` has **both** a `DefaultToolExecutor` pointing to an annotated `@Tool` method **and** `AiServiceTool.builder().toolInputGuardrails(...)` / `toolOutputGuardrails(...)` verifying that both run with the method annotation first.
2. **Subclass class-level annotation hiding parent class-level annotation (Task 06b item 4.2):**
   - [`ToolGuardrailResolutionTest.class_annotation_is_found_on_interfaces_and_superclasses`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/test/java/dev/langchain4j/service/guardrail/ToolGuardrailResolutionTest.java#L517-L522) tests an unannotated subclass inheriting from an annotated superclass/interface, but does not test a subclass that **also** has `@ToolOutputGuardrails` to pin the rule documented in [`ToolGuardrailChainResolver.findClassAnnotation`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailChainResolver.java#L164-L173) (*"the first one found wins, so an annotation on a subclass hides the one of its parents"*).
   - Also worth considering: should that inheritance/override behavior be mentioned in the public Javadoc of [`@ToolInputGuardrails`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolInputGuardrails.java) and [`@ToolOutputGuardrails`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolOutputGuardrails.java), since right now it is only documented on the package-private `ToolGuardrailChainResolver`?

---

## 4. Minor Polish / Nitpicks

1. **Javadoc grammar in [`AgentBuilder.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java):**
   - Lines [738](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java#L738) and [772](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java#L772): `"Sets the {@link ToolInputGuardrail} classes to be instantiated applied to every tool of the agent."` is missing `"and"` (`"to be instantiated and applied"`).
   - Lines [738–803](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java#L738-L803): Unlike [`AiServices.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java#L1366-L1367), the `AgentBuilder` Javadoc does not mention that a global guardrail can skip tools via `toolName()` / `toolSource()` (Task 06b item 2 & 5).
2. **Javadoc wording in [`AiServiceTool.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/AiServiceTool.java):**
   - Lines [75–77](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/AiServiceTool.java#L75-L77) and [87–89](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/AiServiceTool.java#L87-L89) say `"together with the ones of the method annotation, if any"`, whereas [`ToolGuardrailService`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolGuardrailService.java#L35) and Task 06b item 6 specify `"after the ones of the method annotation, if any"`. Similarly, the builder setters at lines [189–191](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/AiServiceTool.java#L189-L191) and [213–215](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/AiServiceTool.java#L213-L215) mention running after the `AiServices` builder and class annotation, but omit the method annotation.
3. **[`ToolServiceContext.toString()`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/tool/ToolServiceContext.java#L184-L191):**
   - `equals` and `hashCode` were updated for `toolGuardrailBindings` (and `toolGuardrailService` in `equals`), but `toString()` was not updated to include them.
4. **Formatting noise in [`DefaultAiServices.java`](file:///Users/edeandre/workspaces/IntelliJ/langchain4j/langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java):**
   - Only 1 semantic line was added (`context.toolService.toolGuardrailService(context.toolGuardrailService());` at line 200), but `spotless:apply` reformatted the entire 2,000-line file because `ratchetFrom` formats any touched file in full. That is expected with Spotless ratcheting, though worth keeping in mind when reviewing the diff.
