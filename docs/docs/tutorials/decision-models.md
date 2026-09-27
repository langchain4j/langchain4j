---
sidebar_position: 38
---

# Decision Models

:::note
The `DecisionModel` API is experimental and may change in future releases.
:::

A decision model answers typed questions about some input, instead of generating text.
You give it a **state** (for example a support ticket) and a set of named **questions**,
and it returns one typed **answer** per question, together with probabilities.

Typical uses are:
- **Classification and routing**: which team should handle this ticket? Which agent or retriever should handle this query?
- **Gating**: is this message spam? Does this answer contain personal data? Does this query need retrieval at all?
- **Grading**: how urgent is this incident? How frustrated is the customer? How well does this answer address the question?

Decision models are usually much faster and cheaper than chat models for these tasks,
and because they return typed answers, there is no text to parse.
All questions of a request are answered against the same state in a single call.

Available implementations are listed [here](/category/decision-models).

## Asking questions

A `DecisionRequest` contains the state and the questions, each registered under a name of your choice:

```java
DecisionModel decisionModel = TypeSafeDecisionModel.builder()
        .apiKey(System.getenv("TYPESAFE_API_KEY"))
        .modelName("jev-latest")
        .build();

DecisionRequest request = DecisionRequest.builder()
        .state("Help! My payouts have been failing for 3 days and nobody answers my emails.")
        .question("team", ChoiceQuestion.builder()
                .instructions("Which team should handle this ticket?")
                .option("billing", "Payments, payouts, invoices, refunds")
                .option("support", "Problems using the product")
                .option("sales", "Pricing, upgrades, new accounts")
                .build())
        .question("urgent", NoulQuestion.builder()
                .instructions("Does this need attention today?")
                .build())
        .question("frustration", ScoreQuestion.builder()
                .instructions("How frustrated is the customer?")
                .level("Calm")
                .level("Frustrated")
                .level("Angry")
                .build())
        .build();

DecisionResponse response = decisionModel.decide(request);
```

The response contains one answer per question, under the same name:

```java
ChoiceAnswer team = response.choice("team");
NoulAnswer urgent = response.noul("urgent");
ScoreAnswer frustration = response.score("frustration");

team.choice();              // "billing"
team.probabilities();       // {billing=0.88, support=0.1, sales=0.02}
urgent.probability();       // 0.93
frustration.score();        // 1.4
frustration.probabilities(); // [0.05, 0.5, 0.45]
```

The response also carries the name of the model that produced the answers and the token usage:
`response.modelName()` and `response.tokenUsage()`.

## Question types

### Yes/no questions

A `NoulQuestion` asks a yes/no question and is answered with a `NoulAnswer`,
whose `probability()` is the probability that the answer is "yes", from 0 to 1.

Optionally, describe when the answer should be "yes" and when it should be "no":

```java
NoulQuestion refundRequested = NoulQuestion.builder()
        .instructions("Does the customer ask for a refund?")
        .whenTrue("The customer explicitly asks for their money back")
        .whenFalse("The customer only asks about a charge")
        .build();
```

### Choice questions

A `ChoiceQuestion` selects exactly one option out of a named set (at least 2 options).
It is answered with a `ChoiceAnswer`:
- `choice()`: the name of the chosen option
- `probabilities()`: the probability of each option, keyed by option name
- `confidence()`: how confident the model is, or `null` if the model does not report it (see [below](#probabilities-and-confidence))

### Score questions

A `ScoreQuestion` places the state on an ordered scale.
Levels are added from lowest to highest, and a level's number is its index, starting at 0.
It is answered with a `ScoreAnswer`:
- `score()`: the probability-weighted mean of the level indexes, from 0 to `n - 1`.
  It can fall between two levels: with the levels "Calm", "Frustrated" and "Angry",
  a score of 1.4 means "between frustrated and angry, closer to frustrated".
- `probabilities()`: the probability of each level, in the same order as the levels
- `confidence()`: how confident the model is, or `null` if the model does not report it

## Describing options and levels

Options, levels and the `whenTrue`/`whenFalse` descriptions can be plain text, as in the examples above,
or structured content (a `Map` or a `List`), which is passed to the model as is.
Structured descriptions are useful to separate what an option covers from what it does not,
or to add examples:

```java
ChoiceQuestion team = ChoiceQuestion.builder()
        .instructions("Which team should handle this ticket?")
        .option("billing", Map.of(
                "what", "Payments, payouts, invoices, refunds",
                "not_for", "Questions about pricing plans",
                "examples", List.of("I was charged twice", "Where is my payout?")))
        .option("sales", "Pricing, upgrades, new accounts")
        .build();
```

The keys are not predefined: choose names that describe the content well, because the model sees them.

## Describing the state

The state can also be plain text, a `Map` or a `List`.
Use a `Map` to give the model several pieces of information that belong together:

```java
DecisionRequest request = DecisionRequest.builder()
        .state(Map.of(
                "ticket", "My payouts have been failing for 3 days",
                "customer_plan", "enterprise",
                "open_tickets", 3))
        .question("urgent", NoulQuestion.builder()
                .instructions("Does this need attention today?")
                .build())
        .build();
```

## Probabilities and confidence

Every answer comes with probabilities, which are the best basis for decisions in your code,
for example "escalate to a human when the two most likely options are close":

```java
List<Double> top = team.probabilities().values().stream()
        .sorted(Comparator.reverseOrder())
        .limit(2)
        .toList();

if (top.get(0) - top.get(1) < 0.2) {
    escalateToHuman(ticket);
}
```

Choice and score answers can also carry a `confidence()` value from 0 to 1.
How it is computed is defined by each model and differs between models,
so a threshold tuned for one model does not carry over to another. Probabilities do.

## Model name and other parameters

The model to use is usually configured when building the `DecisionModel`.
It can also be set per request, which overrides the configured one:

```java
DecisionRequest request = DecisionRequest.builder()
        .state(ticket)
        .question("urgent", urgentQuestion)
        .parameters(DecisionRequestParameters.builder()
                .modelName("jev-1.13.0")
                .build())
        .build();
```

## Asynchronous calls

`decideAsync(request)` returns a `CompletableFuture<DecisionResponse>`.
Implementations that do not support non-blocking calls return a future that fails with `AsyncNotSupportedException`.

## Other question types

`Question` and `DecisionAnswer` are interfaces, so an implementation can support additional question types
with their own answer types. An implementation that receives a question type it does not support
throws `UnsupportedFeatureException` without calling the model.

Answers of such types can be read with `response.answer(name, type)`, for example
`response.answer("next_step", RankAnswer.class)`.
