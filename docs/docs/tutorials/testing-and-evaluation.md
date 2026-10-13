---
sidebar_position: 34
---

# Testing and Evaluation

Testing an LLM application is not the same as testing ordinary code. A normal unit test relies on
two guarantees: the function is deterministic, and there is one right answer. An LLM call has
neither: the same prompt can produce different text on each run, and two different strings can
both be correct answers. So `assertEquals` on the model output breaks in two directions. It fails
on harmless wording changes (a false alarm), and it passes on answers that are fluent but wrong (a
false sense of safety).

Two properties drive everything on this page:

- **Nondeterminism.** Output varies run to run, especially above temperature 0. Any check that
  depends on byte-for-byte equality of generated text is brittle.
- **Semantic correctness.** What you care about is whether the answer is right, grounded, on-topic,
  and safe, not whether it matches a fixed string. Judging that often needs more than `equals`.

A useful frame is to test at levels, from cheap and deterministic to expensive and judgment-based.
The same framing appears in *Your AI Product Needs Evals*, linked in the reading list below.
Level 1 is plain unit and integration tests around your wiring.
Higher levels add datasets of representative inputs, scored by evaluators, with an LLM-as-a-judge
where a deterministic rule cannot express the criterion. When the application is an agent, the
same levels apply to a richer output: the tool calls it made on the way to the answer, not just
the answer itself.

## Integration Tests with JUnit

Start at Level 1. Before scoring quality, confirm the plumbing works: the AI Service is wired,
the model responds, and the response has the right shape. These are ordinary JUnit tests, no extra
framework required.

Keep assertions tolerant of wording. Check for required substrings, length bounds, or format
rather than an exact match. A `String chat(String)` AI Service is enough to exercise the path end
to end; the `assertThat` below comes from [AssertJ](https://assertj.github.io/doc/):

```java
interface Assistant {

    String chat(String userMessage);
}

class AssistantIT {

    static Assistant assistant;

    @BeforeAll
    static void setUp() {
        ChatModel model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_5_MINI)
            .build();

        assistant = AiServices.create(Assistant.class, model);
    }

    @Test
    void should_answer_with_the_capital() {
        String answer = assistant.chat("What is the capital of France?");

        assertThat(answer).containsIgnoringCase("Paris");
    }
}
```

:::note
Integration tests that call a real model need an API key (for example `OPENAI_API_KEY`) and cost
tokens. Gate them behind a tag or profile so they do not run on every local build.
:::

## Examples

[Here](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/test/java/dev/langchain4j/example/CustomerSupportAgentIT.java)
is an example of an integration testing for a Customer Support Agent.
This corresponds to [Level 1: Unit Tests](https://hamel.dev/blog/posts/evals/#level-1-unit-tests).

## Evaluating Quality with Datasets and Evaluators

Integration tests confirm the app runs. They do not tell you how good the answers are across a
range of inputs. The next level adds two pieces:

- A **dataset**: representative inputs paired with expected outputs or reference answers. Version
  it and review changes to it like code. A JSON or CSV file next to your tests is the simplest
  start; teams with several suites often move to centrally managed, versioned datasets. Either
  way, grow it from real traffic and bug reports.
- **Evaluators**: checks that score each answer. Keep the score per item, not only the aggregate,
  so you can see which inputs got worse.

Start with deterministic evaluators: exact match for closed-form answers, substring or regex
checks, format and length bounds. They are free, repeatable, and need no API key, so they can run
on every build. Their limit is expressiveness: no rule can score "is this summary faithful to the
source". Where the criterion resists a rule, move up to an LLM-as-a-judge.

## LLM-as-a-Judge

For open-ended responses (summaries, explanations, support replies) there is no single expected
string, so you score the property you care about instead: helpfulness, faithfulness to context,
tone. An LLM-as-a-judge prompts a second model with your criteria and the answer (usually along
with the question or the retrieved context) and parses a score and a reason from its reply.

A judge is itself an LLM, so it is nondeterministic too. Pin it so its scores stay comparable over
time:

- Set the judge `temperature` to `0`.
- Pin the most specific model id the provider offers, a point release like `gpt-5.4` or a dated
  snapshot id, never a floating alias like `gpt-5` that the provider can move under you.
- Keep the evaluator set fixed. Adding or removing an evaluator changes the scores you compare
  against, so regenerate your reference scores after any change to the set.

## Evaluating Agents

Everything above scores one string: the model's final reply. An agent changes the shape of the
output. Between the question and the answer sits a sequence of decisions: which tools to call,
with which arguments, in which order, and what to do with each result. LangChain4j hands you
that sequence. An AI Service that returns `Result<String>` carries it in
`result.toolExecutions()`, as shown in
[Accessing Executed Tools](/tutorials/tools#accessing-executed-tools) in the
[Tools (Function Calling)](/tutorials/tools) tutorial. The
[Agents and Agentic AI](/tutorials/agents) tutorial covers agents built with
`langchain4j-agentic`, and the `ToolExecutedEvent` listener from the
[Observability](/tutorials/observability#ai-service-observability) tutorial captures the same
data in production.

Score a trace on two axes:

- **The path, deterministically.** Did the agent call the expected tools, in an acceptable
  order, with the right arguments? This is a trajectory check: compare the actual call sequence
  against an expected one. Run it even when the final answer reads well: an agent that answers
  "your booking is canceled" without calling the cancellation tool has not canceled anything.
- **The outcome, with a judge.** Did the agent complete the user's task? No rule can decide
  that from the trace, so this is judge territory, under the same pinning rules as the previous
  section.

Two more signals regress silently while the final answers stay fluent, so score them on every run:

- **Over-calling.** Redundant calls, the same tool with the same arguments again, are how loops
  start, and they cost latency and tokens even when the answer comes out right.
- **Tool errors.** A failed tool call does not stop the model from answering; it papers over
  the failure with confident text. Track the fraction of calls that succeeded.

Multi-turn conversations layer one more level on top, scoring a whole session instead of a
single exchange. Trajectory, error, and efficiency scores are per-item numbers like every other
score on this page, so the baseline-and-gate loop in the next section applies to them unchanged.

## Catching Regressions in CI

Scores on their own do not tell you whether quality moved. The fix is a baseline: the per-item
scores from a known-good run, committed to the repository like any other test fixture. Each later
run produces fresh scores, and a gate compares them against the baseline and fails the build when
quality drops.

Two details make this workable in practice:

- The comparison should read scores, not raw model output, so harmless wording changes stay quiet.
- "Drops" needs a statistical definition. Judge scores wobble run to run, so a useful gate fails
  when the drop across the dataset is statistically significant, or when a single item gets
  sharply worse, rather than on every small dip.

When you change a prompt or a model on purpose, scores move by design. Regenerate the baseline
deliberately, review the diff, and commit it, the same way you would update a snapshot test.

Because the gate is an ordinary failing test, it runs anywhere your tests run: locally, on any CI
runner, on every pull request. The [Third-party Integrations](#third-party-integrations) section
below shows a concrete implementation of this loop.

## Third-party Integrations

LangChain4j ships test assertions for guardrails (`langchain4j-test`), but no dataset, evaluator,
or regression-gate tooling. These community and third-party projects add that layer on top of it.

### Dokimos

[Dokimos](https://github.com/dokimos-dev/dokimos) is a community-maintained evaluation framework
for Java and Kotlin, developed outside the LangChain4j project, with a LangChain4j module
(`dev.dokimos:dokimos-langchain4j`).

#### Why use it?

- Wires a LangChain4j `ChatModel` into an experiment via `LangChain4jSupport`, including RAG and
  async tasks.
- Ships deterministic evaluators and an LLM-as-a-judge that runs on any `ChatModel`.
- Converts the `Result` of a tool-calling AI Service into an `AgentTrace`
  (`LangChain4jSupport.toAgentTrace`) and scores it with agent evaluators.
- Provides a regression gate (`Assertions.assertNoRegression`) that compares a run against a
  committed baseline and fails the build, with a GitHub Actions report action.

#### Getting started

Add the dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-langchain4j</artifactId>
    <version>${latest version here}</version>
</dependency>
```

#### Regression gate example

The smallest useful gate is one JUnit test: run a dataset through the model, score each answer
with the deterministic `ExactMatchEvaluator`, and assert that the scores did not regress against
a committed baseline. It is the loop from
[Catching Regressions in CI](#catching-regressions-in-ci), run as a plain failing test:

```java
@Test
void quality_does_not_regress() {
    ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5.4-mini")
        .build();

    Dataset dataset = Dataset.builder()
        .name("Capitals QA")
        .addExample(Example.of("What is the capital of France? Answer with only the city name.", "Paris"))
        .addExample(Example.of("What is the capital of Japan? Answer with only the city name.", "Tokyo"))
        .build();

    Evaluator exactMatch = ExactMatchEvaluator.builder()
        .name("Exact Match")
        .threshold(1.0)
        .build();

    ExperimentResult result = Experiment.builder()
        .name("capitals-qa")
        .dataset(dataset)
        .task(LangChain4jSupport.simpleTask(model))
        .evaluators(List.of(exactMatch))
        .build()
        .run();

    // dev.dokimos.core.Assertions, not org.junit.jupiter.api.Assertions
    Assertions.assertNoRegression(result, "capitals-qa");
}
```

`LangChain4jSupport.simpleTask(model)` calls the model for each example and writes the answer
under the `output` key, which is what the evaluators read. The questions ask for only the city
name because an unprompted chat model usually answers in a full sentence, which exact match
scores `0.0`. The gate pins the `gpt-5.4-mini` point release for the same reproducibility
reason the judge does, and it calls a live model, so gate it behind the same tag or profile as
the integration tests above.

The string passed to `assertNoRegression` is the baseline name. It resolves to
`src/test/resources/dokimos/baselines/capitals-qa.json`, relative to the test JVM working
directory (the module directory under Maven Surefire; pass an explicit `Path` to
`assertNoRegression` if your runner starts the JVM elsewhere). The first local run writes that
file and passes; review and commit it like a test fixture. Later runs compare fresh scores
against it and fail the test on the statistical definition of a drop given above.

The LLM judge (`LLMJudgeEvaluator`, with any `ChatModel` adapted via
`LangChain4jSupport.asJudge`), the full baseline lifecycle including re-baselining after an
intended change, and the GitHub Actions action that posts the gate verdict as a pull request
comment are covered in the [Dokimos documentation](https://dokimos.dev).

#### Evaluating an agent's tool calls

`LangChain4jSupport.toAgentTrace` turns the `Result` from
[Accessing Executed Tools](/tutorials/tools#accessing-executed-tools) into the trace that
[Evaluating Agents](#evaluating-agents) scores. Suppose `assistant` is the AI Service from
that section: it returns `Result<String>`, with one `@Tool`-annotated method
`String cancelBooking(String bookingNumber)` registered via `.tools(...)`. One test then
checks the path it took:

```java
@Test
void agent_takes_the_expected_path() {
    Result<String> result = assistant.chat("Cancel my booking 123-456");
    AgentTrace trace = LangChain4jSupport.toAgentTrace(result);

    EvalTestCase testCase = EvalTestCase.builder()
        .input("Cancel my booking 123-456")
        .actualOutputs(trace.toOutputMap())
        .expectedOutput("toolCalls", List.of(
                ToolCall.of("cancelBooking", Map.of("bookingNumber", "123-456"))))
        .build();

    EvalResult trajectory = ToolTrajectoryEvaluator.builder().build().evaluate(testCase);
    EvalResult errors = ToolErrorEvaluator.builder().build().evaluate(testCase);
    EvalResult efficiency = ToolEfficiencyEvaluator.builder().build().evaluate(testCase);

    assertThat(trajectory.success()).as("%s", trajectory.reason()).isTrue();
    assertThat(errors.success()).as("%s", errors.reason()).isTrue();
    assertThat(efficiency.success()).as("%s", efficiency.reason()).isTrue();
}
```

`toAgentTrace` reads `result.toolExecutions()` and `result.content()`; `trace.toOutputMap()`
stores the calls under the `toolCalls` key the agent evaluators read. The trajectory
evaluator's default `IN_ORDER` mode grades the sequence by longest common subsequence, so at
the default threshold of `1.0` it passes only on an exact ordered match: one extra call around
the expected one would score `0.5` here and fail. When extra calls are acceptable, switch to
`MatchMode.SUPERSET` or lower the threshold. The error evaluator treats a null or blank tool
result as a failure by default, so `cancelBooking` must return a confirmation string rather
than nothing. Argument names are your Java parameter names, so the expected map only matches
when the build keeps them (the `-parameters` note in the Tools tutorial applies here too). All
three evaluators are deterministic; the only live model call is the agent itself, so gate this
test like the other integration tests. The judge-backed agent checks (task completion,
argument hallucination) and the rest of the agent evaluator set are covered in the
[agent evaluation guide](https://dokimos.dev/evaluation/agent-evaluation).

## Recommended Reading

- [Your AI Product Needs Evals](https://hamel.dev/blog/posts/evals/)
- [Creating a LLM-as-a-Judge That Drives Business Results](https://hamel.dev/blog/posts/llm-judge/)
- [A Practical Guide to RAG Pipeline Evaluation (Part 1: Retrieval)](https://medium.com/relari/a-practical-guide-to-rag-pipeline-evaluation-part-1-27a472b09893)
- [A Practical Guide to RAG Pipeline Evaluation (Part 2: Generation)](https://medium.com/relari/a-practical-guide-to-rag-evaluation-part-2-generation-c79b1bde0f5d)
- [How important is a Golden Dataset for LLM evaluation?](https://medium.com/relari/how-important-is-a-golden-dataset-for-llm-pipeline-evaluation-4ef6deb14dc5)
- [Case Study: Reference-free vs Reference-based evaluation of RAG pipeline](https://medium.com/relari/case-study-reference-free-vs-reference-based-evaluation-of-rag-pipeline-9a49ef49866c)
- [How to evaluate complex GenAI Apps: a granular approach](https://medium.com/relari/how-to-evaluate-complex-genai-apps-a-granular-approach-0ab929d5b3e2)
- [Generate Synthetic Data to Test LLM Applications](https://medium.com/relari/generate-synthetic-data-to-test-llm-applications-4bffeb51b80e)
