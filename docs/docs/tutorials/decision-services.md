---
sidebar_position: 39
---

# Decision Services

:::note
Decision Services are experimental and may change in future releases.
:::

[Decision models](/tutorials/decision-models) answer typed questions about some input: yes/no questions,
questions choosing one of several options, and so on.
Decision Services let you use them through a plain Java interface:
you declare what you want to know as methods, and LangChain4j implements the interface for you.

```java
enum Team {
    @Description("Payments, payouts, invoices, refunds") BILLING,
    @Description("Problems using the product") SUPPORT,
    @Description("Pricing, upgrades, new accounts") SALES
}

interface SupportDesk {

    @Decide("Is this message spam?")
    boolean isSpam(String message);

    @Decide("Which team should handle this ticket?")
    Team route(String ticket);
}

SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
        .decisionModel(decisionModel)
        .build();

boolean spam = supportDesk.isSpam("Congratulations! You won a free cruise!");   // true
Team team = supportDesk.route("I was charged twice this month");                // BILLING
```

Any `DecisionModel` can be used, for example [TypeSafe](/integrations/decision-models/typesafe).

## Maven dependency

Decision Services are part of the `langchain4j` module; add it next to the module of your `DecisionModel`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.21.0</version>
</dependency>
```

Decision Services send the names of the method parameters to the model, so compile your code with the
`-parameters` option (or name each parameter with `@V`, see [Parameters](#parameters)):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

The types used below come from these packages:
- `dev.langchain4j.service.decision`: `DecisionServices`, `@Decide`, `Choice`, `DecisionResult`, `ThresholdProvider`
- `dev.langchain4j.model.decision.response`: `YesNoAnswer`
- `dev.langchain4j.model.decision.request`: `DecisionRequestParameters`
- `dev.langchain4j.model.output.structured.Description` and `dev.langchain4j.service.V`

## How it works

For every call, the Decision Service:
1. Sends the method parameters to the model as the input, keyed by parameter name,
   for example `{"message": "Congratulations! You won a free cruise!"}`.
2. Asks the question(s) derived from the method's return type and the `@Decide` annotation.
3. Converts the answer(s) back to the return type.

All methods are checked when `build()` is called, so a misconfigured method
(an unsupported return type, a missing `@Decide`, etc.) fails immediately with an explanation, not on the first call.

## Return types

| Return type | Question | Result |
|---|---|---|
| `boolean` / `Boolean` | yes/no | `true` if the probability of "yes" reaches the [threshold](#thresholds) |
| `YesNoAnswer` | yes/no | the probability of "yes" |
| an enum | choice between the enum constants | the chosen constant |
| `Choice<E>` (`E` is an enum) | choice between the enum constants | the chosen constant and the probability of each constant |
| a class or record whose fields have the types above | one question per field, all in a single call | an instance with every field set |

Any of these can also be wrapped in [`DecisionResult<T>`](#response-metadata)
and/or [`CompletableFuture<T>` or `CompletionStage<T>`](#asynchronous-calls).

### Yes/no questions

```java
interface Moderation {

    @Decide("Is this message spam?")
    boolean isSpam(String message);

    @Decide("Is this message spam?")
    YesNoAnswer spamProbability(String message);
}

YesNoAnswer spam = moderation.spamProbability(message);
spam.probability();   // 0.97
spam.isYes(0.9);      // true
```

Return `YesNoAnswer` when you want to decide on the threshold in your own code,
or to [evaluate several thresholds](#evaluating-thresholds) without calling the model again.

### Choice questions

The constants of an enum are the options.
The model sees the name of each constant together with its `@Description`,
so choose names and descriptions that explain what each option covers:

```java
@Decide("Which team should handle this ticket?")
Choice<Team> routeWithProbabilities(String ticket);

Choice<Team> choice = supportDesk.routeWithProbabilities(ticket);
choice.value();                      // BILLING
choice.probabilities();              // {BILLING=0.88, SUPPORT=0.1, SALES=0.02}
choice.probability(Team.SUPPORT);    // 0.1
choice.margin();                     // 0.78, the difference between the two most likely constants
choice.confidence();                 // provided by some models, see below
```

A small margin means that the model hesitated between two options, which is a good signal to escalate:

```java
Choice<Team> choice = supportDesk.routeWithProbabilities(ticket);
if (choice.margin() < 0.2) {
    humanQueue.add(ticket);
} else {
    assign(ticket, choice.value());
}
```

`confidence()` is computed differently by each model, so prefer probabilities for thresholds
(see [Probabilities and confidence](/tutorials/decision-models#probabilities-and-confidence)).

### Several questions in one call

When a method returns a class or a record, every field becomes a question,
and all questions are answered in a single call to the model:

```java
record Triage(
        @Decide("Which team should handle this ticket?") Team team,
        @Decide("Does this need attention today?") boolean urgent,
        @Decide("Does the customer ask for money back?") YesNoAnswer refund) {}

interface SupportDesk {

    Triage triage(String ticket, String plan);
}

Triage triage = supportDesk.triage("I was charged twice and our payroll runs today!", "enterprise");
triage.team();                     // BILLING
triage.urgent();                   // true
triage.refund().probability();     // 0.99
```

Every field needs a question: `@Decide`, or `@Description` if there is no `@Decide`
(so classes written for [structured outputs](/tutorials/structured-outputs) can be reused).
A field without either fails when `build()` is called.
`@Decide` on the method itself is not supported for such methods.

A regular class works as well, as long as it has a no-argument constructor:

```java
class Triage {

    @Decide("Which team should handle this ticket?")
    Team team;

    @Decide("Does this need attention today?")
    boolean urgent;
}
```

## Parameters

All parameters are sent to the model as the input, keyed by parameter name.
Parameters that are `null` are left out.

```java
Triage triage(String ticket, Customer customer);
// input: {"ticket": "...", "customer": {"plan": "enterprise", "openTickets": 3}}
```

The names help the model understand what each value means, so choose them well.
Objects are converted to maps using their Java field names.
All fields are sent, so to control exactly what the model sees (and to avoid sending personal data it does not
need), pass a small record containing only the relevant fields.
If all parameters sent to the model are `null`, the call fails with an `IllegalArgumentException`.

Parameter names are only available at runtime when the code is compiled with the `-parameters` option
(projects based on Spring Boot or Quarkus usually enable it). Otherwise, name the parameters with `@V`:

```java
Triage triage(@V("ticket") String ticket, @V("plan") String plan);
```

If a name is not available, `build()` fails and explains both options.

### Model name and other parameters

A parameter of type `DecisionRequestParameters` is not sent as part of the input.
Instead, it sets the parameters of the call, for example the model to use:

```java
@Decide("Which team should handle this ticket?")
Team route(String ticket, DecisionRequestParameters parameters);

Team team = supportDesk.route(ticket, DecisionRequestParameters.builder()
        .modelName("jev-1.13.0")
        .build());
```

## Thresholds

A `boolean` result is `true` when the probability of "yes" is greater than or equal to a threshold.
The threshold is 0.5 by default and can be configured with a `ThresholdProvider`.
It receives a `ThresholdContext` describing the question:
- `serviceInterface()` and `method()`: the service and the method that is invoked;
- `questionName()`: the name of the method, or the name of the field for methods returning an object;
- `modelName()`: the model that answered, as reported by the provider (for example a pinned version rather than
  an alias), so that each model version can have its own thresholds. If the provider does not report it,
  the requested model name.

It is called on every invocation, so the thresholds can come from configuration that changes at runtime:

```java
SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
        .decisionModel(decisionModel)
        .thresholdProvider(context -> config.getDouble(   // e.g. SupportDesk.isSpam=0.9
                context.serviceInterface().getSimpleName() + "." + context.questionName()))
        .build();
```

When the provider returns `null`, the default of 0.5 is used.
Make sure your configuration keys match the question names exactly:
a missing key silently falls back to 0.5, which is rarely what a gate needs.

To decide on the threshold in the calling code instead, return `YesNoAnswer` and use `isYes(threshold)`.

### Evaluating thresholds

To find a good threshold, run the model once per example of a labelled dataset and evaluate all thresholds on the
collected probabilities, without calling the model again:

```java
List<Double> probabilities = dataset.stream()
        .map(example -> moderation.spamProbability(example.text()).probability())
        .toList();

for (int step = 10; step < 20; step++) {
    double threshold = step * 0.05;   // 0.50, 0.55, ..., 0.95
    // compare probabilities >= threshold with the labels, compute precision and recall
}
```

## Response metadata

Wrap the result in `DecisionResult<T>` to also get the raw response of the model,
including the name of the model that answered and the token usage:

```java
@Decide("Which team should handle this ticket?")
DecisionResult<Team> routeWithMetadata(String ticket);

DecisionResult<Team> result = supportDesk.routeWithMetadata(ticket);
result.content();       // BILLING
result.modelName();     // jev-1.13.0
result.tokenUsage();
result.response();      // the DecisionResponse; answers are keyed by the method name (here "routeWithMetadata"),
                        // or by the field names for methods returning an object
```

## Asynchronous calls

Return a `CompletableFuture` or a `CompletionStage` to call the model without blocking:

```java
@Decide("Which team should handle this ticket?")
CompletableFuture<Team> routeAsync(String ticket);
```

Cancelling the returned future cancels the call to the model.

This requires a `DecisionModel` that supports asynchronous calls (see `DecisionModel.decideAsync()`).

## Testing

Decision Services are interfaces, so business code using them can be tested with a mock,
or with a lambda when the interface has a single method:

```java
interface SpamCheck {

    @Decide("Is this message spam?")
    YesNoAnswer isSpam(String message);
}

SpamCheck spamCheck = message -> YesNoAnswer.of(0.97);

CommentService commentService = new CommentService(spamCheck);
assertThat(commentService.accept("Buy now!")).isFalse();
```

Results are easy to create in tests: `YesNoAnswer.of(0.97)`, `Choice.builder()` and `DecisionResult.builder()`.

## Errors

- A misconfigured interface fails when `build()` is called, with an `IllegalConfigurationException` explaining
  the problem.
- If the model returns an answer that does not match the question (for example, an option that is not a constant of
  the enum), the call fails with an `InvalidDecisionResponseException`.
- Errors of the model (authentication, rate limits, timeouts) are thrown as described in
  [Decision Models](/tutorials/decision-models#errors).

## Limitations

- Options are defined with enums. When the options are only known at runtime
  (for example, agents or tools registered in a database), use the
  [`DecisionModel` API](/tutorials/decision-models#choice-questions) directly.
- Scale questions are not supported yet. Use the [`DecisionModel` API](/tutorials/decision-models#scale-questions)
  directly.
