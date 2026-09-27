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

## How it works

For every call, the Decision Service:
1. Sends the method parameters to the model as the state, keyed by parameter name,
   for example `{"message": "Congratulations! You won a free cruise!"}`.
2. Asks the question(s) derived from the method's return type and the `@Decide` annotation.
3. Converts the answer(s) back to the return type.

All methods are checked when `build()` is called, so a misconfigured method
(an unsupported return type, a missing `@Decide`, etc.) fails immediately with an explanation, not on the first call.

## Return types

| Return type | Question | Result |
|---|---|---|
| `boolean` | yes/no | `true` if the probability of "yes" reaches the [threshold](#thresholds) |
| `YesNo` | yes/no | the probability of "yes" |
| an enum | choice between the enum constants | the chosen constant |
| `Choice<E>` (`E` is an enum) | choice between the enum constants | the chosen constant and the probability of each constant |
| a class or record whose fields have the types above | one question per field, all in a single call | an instance with every field set |

Any of these can also be wrapped in [`DecisionResult<T>`](#response-metadata)
and/or [`CompletableFuture<T>`](#asynchronous-calls).

### Yes/no questions

```java
interface Moderation {

    @Decide("Is this message spam?")
    boolean isSpam(String message);

    @Decide("Is this message spam?")
    YesNo spamProbability(String message);
}

YesNo spam = moderation.spamProbability(message);
spam.probability();   // 0.97
spam.isYes(0.9);      // true
```

Return `YesNo` when you want to decide on the threshold in your own code,
or to [evaluate several thresholds](#evaluating-thresholds) without calling the model again.

### Choice questions

The constants of an enum are the options.
The model sees the name of each constant together with its `@Description`,
so choose names and descriptions that explain what each option covers:

```java
@Decide("Which team should handle this ticket?")
Choice<Team> route(String ticket);

Choice<Team> choice = supportDesk.route(ticket);
choice.value();                      // BILLING
choice.probabilities();              // {BILLING=0.88, SUPPORT=0.1, SALES=0.02}
choice.probability(Team.SUPPORT);    // 0.1
choice.margin();                     // 0.78, the difference between the two most likely constants
choice.confidence();                 // provided by some models, see below
```

A small margin means that the model hesitated between two options, which is a good signal to escalate:

```java
Choice<Team> choice = supportDesk.route(ticket);
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
        @Decide("Does the customer ask for money back?") YesNo refund) {}

interface SupportDesk {

    Triage triage(String ticket, String plan);
}

Triage triage = supportDesk.triage("I was charged twice and our payroll runs today!", "enterprise");
triage.team();                     // BILLING
triage.urgent();                   // true
triage.refund().probability();     // 0.99
```

The question of a field is taken from `@Decide`, or from `@Description` if there is no `@Decide`
(so classes written for [structured outputs](/tutorials/structured-outputs) can be reused),
or else from the name of the field.
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

All parameters are sent to the model as the state, keyed by parameter name.
Parameters that are `null` are left out.

```java
Triage triage(String ticket, Customer customer);
// state: {"ticket": "...", "customer": {"plan": "enterprise", "open_tickets": 3}}
```

The names help the model understand what each value means, so choose them well.
Objects are converted to JSON by the `DecisionModel` implementation; field names may be converted as well
(for example, to snake_case). To control exactly what the model sees, pass a small record containing only the
relevant fields.

Parameter names are only available at runtime when the code is compiled with the `-parameters` option
(projects based on Spring Boot or Quarkus usually enable it). Otherwise, name the parameters with `@V`:

```java
Triage triage(@V("ticket") String ticket, @V("plan") String plan);
```

If a name is not available, `build()` fails and explains both options.

### Model name and other parameters

A parameter of type `DecisionRequestParameters` is not sent as part of the state.
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
The threshold is 0.5 by default and can be configured with a `thresholdProvider`,
which receives the name of the question: the name of the method, or the name of the field for methods returning
an object. It is called on every invocation, so the thresholds can come from configuration that changes at runtime:

```java
SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
        .decisionModel(decisionModel)
        .thresholdProvider(question -> config.getDouble("thresholds." + question))   // e.g. thresholds.isSpam=0.9
        .build();
```

When the provider returns `null`, the default of 0.5 is used.

To decide on the threshold in the calling code instead, return `YesNo` and use `isYes(threshold)`.

### Evaluating thresholds

To find a good threshold, run the model once per example of a labelled dataset and evaluate all thresholds on the
collected probabilities, without calling the model again:

```java
List<Double> probabilities = dataset.stream()
        .map(example -> moderation.spamProbability(example.text()).probability())
        .toList();

for (double threshold = 0.5; threshold < 1.0; threshold += 0.05) {
    // compare probabilities >= threshold with the labels, compute precision and recall
}
```

## Response metadata

Wrap the result in `DecisionResult<T>` to also get the raw response of the model,
including the name of the model that answered and the token usage:

```java
@Decide("Which team should handle this ticket?")
DecisionResult<Team> route(String ticket);

DecisionResult<Team> result = supportDesk.route(ticket);
result.content();       // BILLING
result.modelName();     // jev-1.13.0
result.tokenUsage();
result.response();      // the DecisionResponse; answers are keyed by the method name, or the field names
```

## Asynchronous calls

Return a `CompletableFuture` to call the model without blocking:

```java
@Decide("Which team should handle this ticket?")
CompletableFuture<Team> routeAsync(String ticket);
```

This requires a `DecisionModel` that supports asynchronous calls (see `DecisionModel.decideAsync()`).

## Testing

Decision Services are interfaces, so business code using them can be tested with a mock,
or with a lambda when the interface has a single method:

```java
interface SpamCheck {

    @Decide("Is this message spam?")
    YesNo isSpam(String message);
}

SpamCheck spamCheck = message -> new YesNo(0.97);

CommentService commentService = new CommentService(spamCheck);
assertThat(commentService.accept("Buy now!")).isFalse();
```

`YesNo`, `Choice` and `DecisionResult` have public constructors, so they are easy to create in tests.

## Limitations

- Options are defined with enums. When the options are only known at runtime
  (for example, agents or tools registered in a database), use the
  [`DecisionModel` API](/tutorials/decision-models#choice-questions) directly.
- Score questions are not supported yet. Use the [`DecisionModel` API](/tutorials/decision-models#score-questions)
  directly.
