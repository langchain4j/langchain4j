---
sidebar_position: 34
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Testing

An application built on a large language model (LLM) is harder to test than ordinary code:

- the model can answer the same question differently each time;
- calling the model takes time and costs money;
- whether an answer is right is often a question of meaning, not of exact text.

This page shows how to test such an application with JUnit and AssertJ, using the `langchain4j-test` module:

- [Unit tests without a model](#unit-tests-without-a-model): replace the model with a mock that answers what the test
  tells it to. These tests are fast, free and give the same result every time.
- [Checking what an AI Service did](#checking-what-an-ai-service-did): which tools it executed, with which arguments,
  which sources it retrieved, how often it called the model and how it ended.
- [Judging answers with a model](#judging-answers-with-a-model): check what cannot be checked exactly, such as "says
  that the booking cannot be found", with a model as the judge.
- [Dealing with non-determinism](#dealing-with-non-determinism): make tests with a real model reliable, with repeated
  runs, pass rates and pinned model versions.

Start with checks that do not need a model to judge the answer, such as which tool was executed: they are cheap and
exact. Use a judge only for what they cannot check.

:::info Testing or evaluation?
Testing and evaluation answer different questions:

- **Testing** checks that your code works: while you develop and on every build, mostly with mocks and a few cases you
  write, with a pass or fail result. Most tests are fast and cheap, and testing is useful from the first day of a
  project. This page is about testing.
- **Evaluation** measures how good the answers of your application are: with the real model, on many realistic inputs,
  often from real use, with scores that you compare over time. See [Evaluation](/tutorials/evaluation).
:::

:::note
`langchain4j-test` is experimental: its API may change in future releases.
:::

## Setup

Add `langchain4j-test` to your test dependencies. Its version comes from the `langchain4j-bom`:

<Tabs>
  <TabItem value="maven" label="Maven" default>
    ```xml
    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-bom</artifactId>
          <version>1.24.0</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>

    <dependencies>
      <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-test</artifactId>
        <scope>test</scope>
      </dependency>
    </dependencies>
    ```
  </TabItem>
  <TabItem value="gradleGroovy" label="Gradle (Groovy)">
    ```groovy
    testImplementation platform('dev.langchain4j:langchain4j-bom:1.24.0')
    testImplementation 'dev.langchain4j:langchain4j-test'
    ```
  </TabItem>
  <TabItem value="gradleKotlin" label="Gradle (Kotlin)">
    ```kotlin
    testImplementation(platform("dev.langchain4j:langchain4j-bom:1.24.0"))
    testImplementation("dev.langchain4j:langchain4j-test")
    ```
  </TabItem>
</Tabs>

Without the BOM, note that `langchain4j-test` has a beta version: `1.24.0-beta34` goes with LangChain4j `1.24.0`.
The examples also use JUnit and the `langchain4j` module, which provides [AI Services](/tutorials/ai-services).

All assertions on this page, including the AssertJ ones, come with a single static import:

```java
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
```

The examples test this AI Service of a car rental company, which can cancel bookings with a
[tool](/tutorials/tools):

```java
interface Assistant {

    @SystemMessage("You are a customer support agent of a car rental company.")
    String chat(String userMessage);
}

class BookingTools {

    private final Set<String> bookings;
    private final Set<String> cancelledBookings = new HashSet<>();

    BookingTools(String... bookingNumbers) {
        this.bookings = Set.of(bookingNumbers);
    }

    @Tool("Cancels the booking with the given booking number")
    String cancelBooking(String bookingNumber) {
        if (!bookings.contains(bookingNumber)) {
            throw new IllegalArgumentException("Booking " + bookingNumber + " not found");
        }
        cancelledBookings.add(bookingNumber);
        return "Booking " + bookingNumber + " is cancelled";
    }

    boolean isCancelled(String bookingNumber) {
        return cancelledBookings.contains(bookingNumber);
    }
}
```

## Unit tests without a model

`MockChatModel` is a `ChatModel` for tests: it needs no network and answers the way the test tells it to. You script
its answers step by step, one step per request:

```java
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantTest {

    @Test
    void should_cancel_the_booking() {
        MockChatModel model = MockChatModel.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .thenResponds("Your booking MS-777 is cancelled.");
        BookingTools bookingTools = new BookingTools("MS-777");

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(bookingTools)
                .build();

        String answer = assistant.chat("Please cancel my booking MS-777");

        assertThat(bookingTools.isCancelled("MS-777")).isTrue();
        assertThat(answer).isEqualTo("Your booking MS-777 is cancelled.");
    }
}
```

Here is what happens:

1. The AI Service sends the user message to the model.
2. The mock answers with a call of the `cancelBooking` tool.
3. The AI Service executes the tool and sends its result to the model in a second request.
4. The mock answers with the text, which the AI Service returns.

The tool call and the answer are what the test scripted, so the test checks what your own code did with them: here,
that the tool really cancelled the booking. A test like this checks your code around the model: how the AI Service is
set up, what it sends to the model and what it does with the answers. It does not check that a real model calls the
right tool or answers well: for that, run the checks of
[Checking what an AI Service did](#checking-what-an-ai-service-did) with a real model, and use a
[judge](#judging-answers-with-a-model).

The AI Service passes the arguments of a tool call to the tool method's parameters with the same names, here
`bookingNumber`. It knows these names only when your code is compiled with the `-parameters` option of `javac` (in
Maven, `<maven.compiler.parameters>true</maven.compiler.parameters>`), or when you set them with
`@P(name = "bookingNumber")` (see [Parameter Name](/tutorials/tools#parameter-name)). Otherwise the tool does not get
the argument, and only a check of the tool's effect, like `isCancelled(...)` above, notices it.

The rules of a script:

- Every `that...` method creates a mock with its first step (a method that takes several messages creates one step
  per message), and every `then...` method adds a step.
- Each step answers exactly one request. A request after the last step fails with an `IllegalStateException` that
  shows the request, so the test notices when your code calls the model more often than expected.
- To answer every further request with the last step, end the script with `thenRepeats()`. A step that calls a tool
  cannot repeat.

  ```java
  MockChatModel model = MockChatModel.thatResponds("Hello!").thenRepeats();
  ```

- To test how your code handles a failing model, for example your retry or fallback logic, let a step fail with the
  kind of exception a model provider throws:

  ```java
  MockChatModel flaky = MockChatModel.thatFails(new InternalServerException("Service unavailable"))
          .thenResponds("Hello!");
  ```

- A step can compute its answer from the request, with `thatResponds(Function<ChatRequest, AiMessage>)`. Followed by
  `thenRepeats()`, it answers every request this way, for example requests that arrive in parallel.
- For an AI Service method that returns an object, let the mock answer with its JSON, for example
  `MockChatModel.thatResponds("{\"bookingNumber\": \"MS-777\"}")`. By default, the mock reports no capabilities, so
  the AI Service adds format instructions to the user message. To test the JSON schema response format instead, add
  `withSupportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)`.

A mock remembers which steps were taken and which requests it received, so create a new one for each test.

### What the model received

To check what your code sent to the model, use the requests the mock recorded. `requests()` returns all of them,
`request()` the only one and `lastRequest()` the last one:

```java
assertThat(model.requests().get(0))
        .hasSystemMessageContaining("customer support agent")
        .hasLastUserMessageContaining("MS-777")
        .hasToolSpecification("cancelBooking");
```

### Streaming

`MockStreamingChatModel` does the same for a `StreamingChatModel`. Each step can stream its answer in parts:

```java
interface StreamingAssistant {

    @SystemMessage("You are a customer support agent of a car rental company.")
    TokenStream chat(String userMessage);
}
```

```java
@Test
void should_stream_the_answer() throws Exception {
    MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Your booking ", "MS-777 ", "is cancelled.");

    StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
            .streamingChatModel(model)
            .build();

    List<String> tokens = new CopyOnWriteArrayList<>();
    CompletableFuture<ChatResponse> response = new CompletableFuture<>();
    assistant.chat("Please cancel my booking MS-777")
            .onPartialResponse(tokens::add)
            .onCompleteResponse(response::complete)
            .onError(response::completeExceptionally)
            .start();

    assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("Your booking MS-777 is cancelled.");
    assertThat(tokens).containsExactly("Your booking ", "MS-777 ", "is cancelled.");
}
```

To test how your code handles a stream that breaks off, let the stream fail after its first parts:

```java
MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Your booking ")
        .andFails(new RuntimeException("Connection reset"));
```

[Streaming and asynchronous methods](#streaming-and-asynchronous-methods) shows a shorter way to test a streaming AI
Service.

### Other models

There is a mock for each of these models. They all follow the same rules: `that...` and `then...` steps,
`thenRepeats()`, `thatFails(...)`, and the recorded `requests()`.

| Mock | Replaces | Example |
|---|---|---|
| `MockChatModel` | `ChatModel` | `MockChatModel.thatResponds("Hello!")` |
| `MockStreamingChatModel` | `StreamingChatModel` | `MockStreamingChatModel.thatStreams("Hel", "lo!")` |
| `MockEmbeddingModel` | `EmbeddingModel` | `MockEmbeddingModel.thatEmbeds(0.1f, 0.2f, 0.3f).thenRepeats()` |
| `MockScoringModel` | `ScoringModel` | `MockScoringModel.thatScores(0.9).thenRepeats()` |
| `MockModerationModel` | `ModerationModel` | `MockModerationModel.thatDoesNotFlag().thenRepeats()` |
| `MockDecisionModel` | `DecisionModel` | `MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9).thenRepeats()` |

See the [Javadoc](https://docs.langchain4j.dev/apidocs/dev/langchain4j/test/mock/package-summary.html) of the mocks for
all options.

## Checking what an AI Service did

The answer is only part of what an AI Service does: it may also execute tools, retrieve content and call the model
several times. `AiServiceCapture` captures all of this while your code runs:

```java
import static dev.langchain4j.test.service.AiServiceCapture.capture;
```

```java
@Test
void should_cancel_the_booking() {
    MockChatModel model = MockChatModel.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
            .thenResponds("Your booking MS-777 is cancelled.");

    Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .tools(new BookingTools("MS-777"))
            .build();

    AiServiceCapture capture = capture(() -> assistant.chat("Please cancel my booking MS-777"));

    assertThat(capture)
            .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
            .hasNoFailedToolExecutions()
            .hasOwnChatModelCallCount(2)
            .toolExecutions()
            .extracting(ToolExecution::result)
            .containsExactly("Booking MS-777 is cancelled");
}
```

`capture(...)` works with every method of an AI Service created with `AiServices`, whatever it returns:
`capture.output()` is what the AI Service produced, and `capture.outputText()` its text. These shortcuts, and `assertThat(capture)`, need exactly one AI Service
call in the lambda. When there are several, pick one with `capture.invocation(Assistant.class)` or
`capture.invocations()`.

`hasToolExecutedWith(...)` checks the arguments as the model sent them, not what the tool method received (see the
`-parameters` note above): check that with the tool's result, as above, or with its effect.

These checks do not depend on how the model words its answer, so you can also run them with a real model, for example
to check that the model calls the right tool. A real model may write an argument in several ways, such as `MS-777` or
`ms-777`: check such arguments with your own requirements:

```java
assertThat(capture).hasToolExecutedWith("cancelBooking", arguments ->
        assertThat((String) arguments.get("bookingNumber")).isEqualToIgnoringCase("MS-777"));
```

The most common assertions:

| Assertion | Checks that |
|---|---|
| `hasToolExecuted(name)` | the tool was executed successfully at least once |
| `hasToolExecutedWith(name, arguments)` | the tool was executed successfully, with arguments (as the model sent them) that contain the given ones |
| `hasToolExecutedWith(name, requirements)` | the same, with arguments that pass the given requirements |
| `hasToolExecutedTimes(name, times)` | the tool was executed successfully exactly this number of times |
| `hasToolsExecutedInOrder(names...)` | the tools were executed successfully in this order, possibly with other tools in between. For tools the model asked for in one response, this is the order in which it listed them |
| `hasToolsExecutedExactlyInAnyOrder(names...)` | exactly these tools were executed successfully, in any order, and no other tool was executed |
| `hasNoMoreToolExecutions()` | every tool execution was checked by one of the assertions above |
| `hasNoToolExecutions()` | no tool was executed |
| `hasToolExecutionFailed(name)` / `hasNoFailedToolExecutions()` | a tool execution failed / none failed |
| `hasRetrievedSourceContaining(text)` / `hasNoRetrievedSources()` | a retrieved source contains the text / the retrieval found nothing |
| `hasOwnChatModelCallCount(count)` / `hasOwnChatModelCallCountAtMost(max)` | the AI Service called its chat model exactly / at most this many times |
| `hasFinishReason(reason)` | the final response finished for this reason, for example `FinishReason.STOP` |
| `hasSucceeded()` / `hasFailedWith(type)` / `hasFailedWithMessageContaining(text)` | the invocation succeeded / failed with an error of this type / with an error whose message contains the text |

The assertions on executed tools count only successful executions: add `hasNoFailedToolExecutions()` when every
attempt must succeed. When the AI Service calls another AI Service, for example from a tool, choose first whether the
assertions include it: `assertThat(capture).includingNested()` or `topLevelOnly()`. See the
[Javadoc](https://docs.langchain4j.dev/apidocs/dev/langchain4j/test/service/AiServiceInvocationAssert.html) for all
assertions.

The requests are captured too, for example `capture.lastRequest()`:

```java
assertThat(capture.lastRequest()).hasLastUserMessageContaining("MS-777");
```

### Retrieved content

For an AI Service with [retrieval-augmented generation (RAG)](/tutorials/rag), check what was retrieved and that it
reached the model:

```java
@Test
void should_send_the_rental_terms_to_the_model() {
    ContentRetriever rentalTerms =
            query -> List.of(Content.from("Cancellations are free up to 24 hours before pick-up."));

    Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(MockChatModel.thatResponds("Yes, up to 24 hours before pick-up."))
            .contentRetriever(rentalTerms)
            .build();

    AiServiceCapture capture = capture(() -> assistant.chat("Can I cancel for free?"));

    assertThat(capture).hasRetrievedSourceContaining("24 hours");
    assertThat(capture.lastRequest()).hasLastUserMessageContaining("Cancellations are free");
}
```

In your own tests, use the content retriever of your application, so that the test checks what it retrieves.

### Failures

By default, when a tool throws an exception, the AI Service sends the error to the model, which can then answer, so
the invocation succeeds. Check the failed tool execution:

```java
@Test
void should_not_cancel_an_unknown_booking() {
    MockChatModel model = MockChatModel.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-999"))
            .thenResponds("I could not find booking MS-999.");

    Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .tools(new BookingTools("MS-777"))
            .build();

    AiServiceCapture capture = capture(() -> assistant.chat("Please cancel my booking MS-999"));

    assertThat(capture).hasSucceeded().hasToolExecutionFailed("cancelBooking");
}
```

When the AI Service itself fails, `capture(...)` throws its error. To check a failure the test expects, use
`captureExpectingFailure(...)` (also a static import of `AiServiceCapture`):

```java
@Test
void should_fail_when_the_model_is_unavailable() {
    Assistant assistant = AiServices.create(
            Assistant.class, MockChatModel.thatFails(new InternalServerException("Service unavailable")));

    AiServiceCapture capture = captureExpectingFailure(() -> assistant.chat("Please cancel my booking MS-777"));

    assertThat(capture).hasFailedWith(InternalServerException.class);
}
```

`capture(...)` throws even when your code recovered from the failure, for example with a retry. Test retry and
fallback logic on its result, without `capture(...)`.

### Streaming and asynchronous methods

`capture(...)` returns when the AI Service has finished, also for streaming and asynchronous methods. Start a
`TokenStream` inside the lambda:

```java
@Test
void should_stream_the_answer() {
    StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
            .streamingChatModel(MockStreamingChatModel.thatStreams("Your booking ", "MS-777 ", "is cancelled."))
            .build();

    AiServiceCapture capture = capture(
            () -> assistant.chat("Please cancel my booking MS-777").ignoreErrors().start());

    assertThat(capture.outputText()).isEqualTo("Your booking MS-777 is cancelled.");
}
```

`ignoreErrors()` only tells the `TokenStream` that it has no `onError` handler: `capture(...)` still throws when the
stream fails.

A `CompletableFuture` returned by the lambda is waited for. A Reactor `Flux` or `Mono`, or a Mutiny `Multi` or `Uni`,
returned by the lambda is subscribed to by `capture(...)` and read to the end: return it without subscribing to it,
and find its items in `capture.lambdaOutputItems()`.

`capture(...)` captures the AI Services called on the thread that runs the lambda, and those they call in turn, for
example from a tool. AI Services called on other threads are not captured, for example by an HTTP request to a running server or by an `@Async` method. That way, tests
that run in parallel do not see each other's AI Services. `capture(...)` blocks until the AI Services have finished,
so call it on the test thread, never on an event-loop thread. See the
[Javadoc](https://docs.langchain4j.dev/apidocs/dev/langchain4j/test/service/AiServiceCapture.html) for the details.

## In Quarkus and Spring Boot

- In a Quarkus application, see the
  [testing guide of Quarkus LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html).
- In a Spring Boot application, see [Testing](/tutorials/spring-boot-integration#testing) on the Spring Boot
  Integration page.

## Judging answers with a model

Some things cannot be checked exactly: a real model writes "I could not find booking MS-999" in one run and "There
is no booking with the number MS-999" in the next. A judge is a model that checks whether a text satisfies criteria
written in natural language, an approach often called LLM-as-a-judge. Create one from a capable model:

```java
ChatModel judgeModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-6.1-terra")
        .strictJsonSchema(true)
        .build();

Judge judge = Judge.from(judgeModel);
```

A judge can be shared between tests. Use it in a test of an AI Service that runs on a real model, here with the
`new BookingTools("MS-777")` from above, so that booking MS-999 does not exist:

```java
@Test
void should_say_that_the_booking_does_not_exist() {
    String answer = assistant.chat("Please cancel my booking MS-999");

    judge.assertThat(answer)
            .given("Please cancel my booking MS-999")
            .satisfies("says that booking MS-999 cannot be found", "does not say that a booking was cancelled");
}
```

`given(...)` tells the judge what the text answers. The assertion fails when at least one criterion is not satisfied,
and the message shows the judge's reasoning for each criterion that is not:

```
Expected the text to satisfy 2 criteria, but 2 did not:

Criterion: says that booking MS-999 cannot be found
Verdict: NOT_SATISFIED: The response says that booking MS-999 is cancelled, not that it cannot be found.

Criterion: does not say that a booking was cancelled
Verdict: NOT_SATISFIED: The response says that booking MS-999 is cancelled.

Text:
Your booking MS-999 is cancelled.

User message:
Please cancel my booking MS-999
```

A judge that is not sure (`NOT_SURE`) counts as not satisfied.

Tests with a real model need the API key. Skip them when it is missing, so that a build without the key still passes,
for example with `@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")` on the test class.

### Chat model or decision model as the judge

- A judge created from a `ChatModel` explains each verdict, as above. The model must support JSON schema response
  formats. All criteria of an assertion are judged in one request; with
  `Judge.from(judgeModel).withOneRequestPerCriterion()`, each criterion gets its own request, so the criteria cannot
  influence each other, at a higher cost.
- A judge created from a [`DecisionModel`](/tutorials/decision-models) answers each criterion with the probability
  that it is satisfied. A criterion passes when this probability is at least 0.5; change this with
  `Judge.from(decisionModel).withThreshold(0.7)`. Models calibrate their probabilities differently, so choose a
  threshold with answers you labelled yourself (see
  [Probabilities and confidence](/tutorials/decision-models#probabilities-and-confidence)). This judge gives no
  reasoning, and it cannot take both a conversation and separate sources: put the sources into the conversation then.

```java
Judge judge = Judge.from(decisionModel);

judge.assertThat(answer).satisfies("says that booking MS-999 cannot be found");
```

### Writing good criteria

Vague criteria give inconsistent verdicts:

- Find your criteria in real answers: run your AI Service on realistic questions, including unusual ones, read the
  answers, and write a criterion for each kind of mistake you see.
- Write one criterion for each thing that can go wrong.
- Say exactly what you mean: "says that the return period is 14 days" rather than "answers the question".
- When you know the expected answer, turn each fact it must contain into a criterion.
- When the meaning of a criterion could be disputed, say when it is satisfied and when not:

```java
judge.assertThat(answer)
        .given("Please cancel my booking MS-999")
        .satisfies(JudgeCriterion.from("offers a next step")
                .satisfiedWhen("it suggests what the customer can do now, for example check the booking number")
                .notSatisfiedWhen("it only says that the booking cannot be found"));
```

### Grounding and conversations

To check that a RAG answer only says what the retrieved sources say, and does not make up facts (hallucinate), give
the judge the sources:

```java
AiServiceCapture capture = capture(() -> assistant.chat("Can I cancel for free?"));

judge.assertThat(capture.outputText())
        .given("Can I cancel for free?")
        .basedOnContents(capture.invocation().sources())
        .satisfies("contains only claims that are stated in the sources");
```

Prefer this wording to "is supported by the sources": judges often pass that one even when the text adds a claim that
is not in the sources.

To judge an answer in the context of the conversation, including the tool calls and their results, give the judge the
messages of the last request that the AI Service sent to the model:

```java
AiServiceCapture capture = capture(() -> assistant.chat("Please cancel my booking MS-777"));

judge.assertThat(capture.outputText())
        .given(capture.lastRequest().messages())
        .satisfies("confirms the cancellation only if the cancelBooking tool reported success");
```

Images and other media in these messages are sent to the judge, so its model must support them.

To change the instructions the judge model receives, give a chat model judge your own system message with
`withSystemMessage(...)`, or a decision model judge your own question for each criterion with `withQuestion(...)`.
To judge in another way, for example with several judges that vote, implement `Judge`: `judge.assertThat(...)` works
with any implementation.

### Choosing the judge model

Judges make mistakes, and their verdicts can vary between runs:

- Use a capable model.
- Prefer a model from a different family than the one your application uses.
- Pin a specific model version, not an alias that the provider can point to a newer model, so that results stay
  comparable. Providers retire versions after some months: check their deprecation pages, and move to a new version
  before the old one shuts down.
- Many current models are reasoning models that only accept their default temperature: do not set one for them. For a
  model that accepts it, a temperature of 0 makes verdicts vary less, but not disappear.
- For a chat model, enable strict JSON schema where the provider supports it, as in the example above.

Every assertion calls the judge model, so it takes time and costs money like any other model call. To track the cost,
add a [`ChatModelListener`](/tutorials/observability#chat-model-observability) to a chat judge model, or a
`DecisionModelListener` to a decision judge model.

## Dealing with non-determinism

- **Run checks that need no judge first.** Unit tests with mocks run on every build. Tests with a real model, and
  judges, run less often, for example as integration tests: `*IT` classes, run by the Maven Failsafe plugin (add it to
  your build: the default test run does not run `*IT` classes).
- **One run is not a verdict.** A test with a real model can pass today and fail tomorrow with the same code. For the
  important cases, run the AI Service several times and require a pass rate. `judge.evaluate(...)` returns the
  verdicts instead of failing:

  ```java
  int runs = 10;
  int passed = 0;
  for (int i = 0; i < runs; i++) {
      String answer = assistant.chat("Can I cancel for free?");

      JudgeResponse response = judge.evaluate(JudgeRequest.builder()
              .text(answer)
              .context(JudgeContext.from("Can I cancel for free?"))
              .criteria(JudgeCriterion.from("says that cancellations are free up to 24 hours before pick-up"))
              .build());
      if (response.verdicts().get(0).isSatisfied()) {
          passed++;
      }
  }
  assertThat(passed).isGreaterThanOrEqualTo(8);
  ```

  The runs must not influence each other: an AI Service with [chat memory](/tutorials/chat-memory) must start a new
  conversation in each run, for example with a new memory id. `evaluate(...)` still throws a `JudgeException` when the
  judge's answer cannot be understood.

- **Choose the required pass rate with a margin.** The number of passes varies between runs of the test even when
  nothing changed. An AI Service that answers well 9 times out of 10 on average gets fewer than 9 passes out of 10 in
  about one test run out of four, and fewer than 8 in about one out of fourteen. Require fewer passes than you usually
  see, or run more times.
- **Do not use a pass rate for what must never happen**, such as cancelling the booking of another customer. Check
  that in every run without a judge, for example that `cancelBooking` was not executed, or better, prevent it in your
  code.
- **Pin the model versions** of both your application and the judge. Treat a new model version like a new version of
  a library: run the tests and compare the pass rates before you switch. With few runs, a small difference between two
  pass rates is usually chance.
- **Check the judge.** Before you rely on a judge, run it on a few answers that you labelled yourself, including bad
  ones, and see whether it agrees with you.

## Testing guardrails

`langchain4j-test` also has assertions for [guardrails](/tutorials/guardrails): see
[Unit Testing Input Guardrails](/tutorials/guardrails#unit-testing-input-guardrails) and
[Unit Testing Output Guardrails](/tutorials/guardrails#unit-testing-output-guardrails).
