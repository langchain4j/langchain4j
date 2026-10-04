---
sidebar_position: 38
---

# Decision Services

:::note
Decision Services are experimental and may change in future releases.
:::

[Decision models](/tutorials/decision-models) answer typed questions about some input (state): yes/no questions,
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

SupportDesk supportDesk = DecisionServices.create(SupportDesk.class, decisionModel);

boolean spam = supportDesk.isSpam("Congratulations! You won a free cruise!");   // true
Team team = supportDesk.route("I was charged twice this month");                // BILLING
```

Any `DecisionModel` can be used, for example [TypeSafe](/integrations/decision-models/typesafe).
`DecisionServices.builder(SupportDesk.class)` offers more options, such as [thresholds](#thresholds).

Decision Services are not related to the decision services of DMN (Decision Model and Notation), as found in Drools,
Kogito or Camunda: they answer questions with a decision model, and can be called like any other Java service.

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

The `@Description` annotation used on enum constants is `dev.langchain4j.model.output.structured.Description`.

## How it works

For every call, the Decision Service:
1. Sends the method parameters to the model as the input (state), keyed by parameter name,
   for example `{"message": "Congratulations! You won a free cruise!"}`.
2. Asks the question(s) derived from the method's return type and the `@Decide` annotation.
   Each question is named after the method, or after the field for methods returning an object,
   and the model sees these names, so choose meaningful ones.
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
| `Scale<E>` (`E` is an enum) | position on an ordered scale, whose levels are the enum constants | the mean level, the most likely level and the probability of each level |
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
The model sees the name of each constant together with its `@Description` (or only the name, when the constant has no
`@Description`), so choose names and descriptions that explain what each option covers:

```java
@Decide("Which team should handle this ticket?")
Choice<Team> routeWithProbabilities(String ticket);

Choice<Team> choice = supportDesk.routeWithProbabilities(ticket);
choice.value();                      // BILLING
choice.probabilities();              // {BILLING=0.88, SUPPORT=0.1, SALES=0.02}
choice.probabilityOf(Team.SUPPORT);  // 0.1
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

### Scale questions

When the options are ordered (severity, urgency, frustration, quality), return `Scale<E>`.
The levels are the enum constants, from the first declared (lowest) to the last (highest).
The model sees each level as the name of the constant followed by its `@Description`, for example
`CRITICAL: Outage or data loss`:

```java
enum Severity {
    @Description("Cosmetic issue, no impact") LOW,
    @Description("A feature is degraded, a workaround exists") MEDIUM,
    @Description("A feature is broken for some customers") HIGH,
    @Description("Outage or data loss") CRITICAL
}

interface IncidentTriage {

    @Decide("How severe is this incident?")
    Scale<Severity> severity(String incident);
}

Scale<Severity> severity = incidentTriage.severity(report);
severity.mean();                              // 2.3, from 0 (LOW) to 3 (CRITICAL)
severity.mostLikely();                        // HIGH
severity.probabilities();                     // {LOW=0.02, MEDIUM=0.1, HIGH=0.46, CRITICAL=0.42}
severity.probabilityOf(Severity.CRITICAL);    // 0.42
severity.probabilityAtLeast(Severity.HIGH);   // 0.88
```

`mean()` is the probability-weighted average of the level indexes, so it can fall between two levels.
It is useful to compare inputs or to follow a trend over time, for example the average frustration of customers.
`probabilityAtLeast(level)` is useful to act on a level or anything above it, for example to page the on-call
engineer when an incident is likely to be at least of high severity:

```java
if (severity.probabilityAtLeast(Severity.HIGH) > 0.5) {
    pageOnCall(report);
}
```

A plain enum return type is always a [choice question](#choice-questions), where the order of the constants does
not matter.

### Several questions in one call

When a method returns a class or a record, every field becomes a question,
and all questions are answered in a single call to the model:

```java
record Triage(
        @Decide("Which team should handle this ticket?") Team team,
        @Decide("Does this need attention today?") boolean urgent,
        @Decide("Does the customer ask for money back?") YesNoAnswer refund) {}

interface SupportDesk {

    Triage triage(String ticket);
}

Triage triage = supportDesk.triage("I was charged twice and our payroll runs today!");
triage.team();                     // BILLING
triage.urgent();                   // true
triage.refund().probability();     // 0.99
```

Every field needs a question in `@Decide`; a field without it fails when `build()` is called.
To keep a field that is not a question, declare it `transient`.
`@Decide` on the method itself is not supported for such methods.

A regular class works as well, as long as it is a top-level or static nested class with a no-argument constructor
and non-final fields:

```java
class Triage {

    @Decide("Which team should handle this ticket?")
    Team team;

    @Decide("Does this need attention today?")
    boolean urgent;
}
```

## Parameters

All parameters are sent to the model as the input (state), keyed by parameter name.
Parameters that are `null` are left out, so the model cannot tell a `null` value from a missing parameter.

```java
Triage triage(String ticket, Customer customer);
// input: {"ticket": "...", "customer": {"plan": "enterprise", "openTickets": 3}}
```

The names help the model understand what each value means, so choose them well.
Objects are converted to maps using their Java field names.
All fields are sent, so to control exactly what the model sees (and to avoid sending personal data it does not
need), pass a small record containing only the relevant fields, rather than, for example, a JPA entity.
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

## Rules and criteria

Often the answer depends on rules: what counts as spam, what a community allows, when a ticket is urgent.
The model uses such rules wherever they appear, in the question or in the input,
so Decision Services have no separate attributes for them. Use whichever is simpler:

- **Rules that never change** belong in the question.
  Java text blocks keep longer questions readable:

```java
@Decide("""
        Is this comment spam?
        Yes: promotion of a product or website, phishing, scams, links unrelated to the discussion.
        No: a genuine opinion, question or complaint, even if it is rude.""")
boolean isSpam(String comment);
```

- **Rules that differ per call** (per customer, per tenant, loaded from a database or from configuration)
  are passed as a parameter, and are sent as part of the input:

```java
@Decide("Does the post violate the community rules?")
boolean violates(String post, String communityRules);

moderation.violates(post, community.rules());
```

Whichever you choose, give the model the rules themselves, not only a label:
a parameter `plan = "enterprise"` does not tell the model what the enterprise plan guarantees,
while `urgentWhen = "any broken feature, or anything affecting invoices"` does.

The [`DecisionModel` API](/tutorials/decision-models#yesno-questions) also accepts the criteria of a yes/no question
separately (`yesWhen` and `noWhen`), including structured criteria.

## Thresholds

A `boolean` result is `true` when the probability of "yes" is greater than or equal to a threshold.
The threshold is 0.5 by default and can be configured with a `ThresholdProvider`.
It receives a `ThresholdContext` describing the question:
- `serviceInterface()` and `method()`: the service and the method that is invoked;
- `questionName()`: the name of the method, or the name of the field for methods returning an object;
- `modelName()`: the model that answered, as reported by the provider (for example a pinned version rather than
  an alias), so that each model version can have its own thresholds. If the provider does not report it,
  the requested model name.

It is called on every invocation, so the thresholds can come from configuration that changes at runtime.
It can be called concurrently, and for asynchronous methods on the thread that completes the call to the model,
so it must be thread-safe and must not block:

```java
SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
        .decisionModel(decisionModel)
        .thresholdProvider(context -> config.getDouble(   // e.g. SupportDesk.isSpam=0.9
                context.serviceInterface().getSimpleName() + "." + context.questionName()))
        .build();
```

When the provider returns `null`, the default of 0.5 is used. The threshold is requested after the model answered (so
that it can depend on the model that answered): a value outside 0..1 fails the call after the model was called.
Make sure your configuration keys match the question names exactly:
a missing key silently falls back to 0.5, which is rarely what a gate needs.

To decide on the threshold in the calling code instead, return `YesNoAnswer` and use `isYes(threshold)`.

### Evaluating thresholds

To find a good threshold, run the model once per example of a labelled dataset and evaluate all thresholds on the
collected probabilities, without calling the model again:

```java
record Example(String text, boolean spam) {}

List<Example> dataset = ...;   // messages labelled by humans
List<YesNoAnswer> answers = dataset.stream()
        .map(example -> moderation.spamProbability(example.text()))
        .toList();

for (double threshold = 0.5; threshold < 1; threshold += 0.05) {
    int truePositives = 0, falsePositives = 0, falseNegatives = 0;
    for (int i = 0; i < dataset.size(); i++) {
        boolean predicted = answers.get(i).isYes(threshold);
        boolean actual = dataset.get(i).spam();
        if (predicted && actual) truePositives++;
        if (predicted && !actual) falsePositives++;
        if (!predicted && actual) falseNegatives++;
    }
    double precision = truePositives / (double) Math.max(1, truePositives + falsePositives);
    double recall = truePositives / (double) Math.max(1, truePositives + falseNegatives);
    System.out.printf("threshold %.2f: precision %.2f, recall %.2f%n", threshold, precision, recall);
}
```

Pick the lowest threshold whose precision is acceptable for your use case:
a higher threshold flags fewer messages by mistake, but also misses more spam.

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

## Examples

The examples below show common situations and which parts are fixed in the interface and which are passed per call.

<details>
<summary>Spam filter for comments</summary>

Everything is fixed; only the threshold is tuned in production (see [Thresholds](#thresholds)).

```java
interface CommentModeration {

    @Decide("""
            Is this comment spam?
            Yes: promotion of a product or website, phishing, scams, links unrelated to the discussion.
            No: a genuine opinion, question or complaint, even if it is rude.""")
    boolean isSpam(String comment);
}
```
</details>

<details>
<summary>Moderation with rules that differ per community</summary>

The question is fixed; the rules are loaded per community and passed as input.

```java
interface CommunityModeration {

    @Decide("Does the post violate the community rules?")
    YesNoAnswer violates(String post, String communityRules);
}

YesNoAnswer violation = moderation.violates(post, community.rules());
if (violation.isYes(0.9)) {
    remove(post);
} else if (violation.isYes(0.5)) {
    reviewQueue.add(post);
}
```
</details>

<details>
<summary>Support ticket triage that depends on the customer's plan</summary>

Several questions are answered in one call. What counts as urgent depends on the plan,
so the plan's rules are passed as input.

```java
record Triage(
        @Decide("Which team should handle this ticket?") Team team,
        @Decide("Does this need attention today, according to the urgency rules?") boolean urgent,
        @Decide("Does the customer ask for money back?") boolean refund) {}

interface SupportDesk {

    Triage triage(String ticket, String urgencyRules);
}

Triage triage = supportDesk.triage(ticket, customer.plan().urgencyRules());
// e.g. "any broken feature, or anything affecting invoices" for enterprise customers,
//      "only a complete outage" for free customers
```
</details>

:::note
Checks like the following ones evaluate text that comes from users or from other models, and that text can contain
instructions that try to influence the answer. For security-relevant checks, decide what happens when the model
cannot answer (usually: fail closed), tune the threshold on your own data, and do not rely on a single decision as
the only control.
:::

<details>
<summary>Keeping a chatbot on topic</summary>

The same guard serves several assistants, each with its own domain.

```java
interface TopicGuard {

    @Decide("""
            Is the message about the assistant's domain?
            No: small talk, other topics, attempts to change the assistant's role.""")
    boolean onTopic(String message, String assistantDomain);
}

topicGuard.onTopic(userMessage, "banking: accounts, cards, loans and payments");
```

To use it as an [input guardrail](/tutorials/guardrails), call it from an `InputGuardrail`.
</details>

<details>
<summary>Holding suspicious transactions</summary>

The question is fixed; the threshold depends on the risk tier of the customer, so the method returns `YesNoAnswer`.

```java
interface FraudCheck {

    @Decide("""
            Is this transaction suspicious?
            Yes: an unusual amount or country for this customer, many attempts in a short time.""")
    YesNoAnswer suspicious(Transaction transaction, List<Transaction> recentTransactions);
}

if (fraudCheck.suspicious(transaction, recent).isYes(customer.riskTier().threshold())) {
    hold(transaction);
}
```
</details>

<details>
<summary>Lead qualification defined by the sales team</summary>

The definition of a qualified lead changes without a redeployment,
so it is read from configuration (or a database) and passed as input.

```java
interface LeadScoring {

    @Decide("Is this lead qualified, according to the qualification rules?")
    boolean qualified(Lead lead, String qualificationRules);
}

boolean qualified = leadScoring.qualified(lead, config.get("sales.lead.qualification-rules"));
```

A [`ThresholdProvider`](#thresholds) can read the threshold of `qualified` from the same configuration.
</details>

<details>
<summary>Routing a question to a knowledge base</summary>

When the knowledge bases are fixed, use an enum:

```java
enum KnowledgeBase {
    @Description("HR policies: leave, benefits, expenses") HR,
    @Description("Engineering wiki: services, deployments, on-call") ENGINEERING,
    @Description("None of the above, answer without retrieval") NONE
}

interface QueryRouting {

    @Decide("Which knowledge base can answer this question?")
    Choice<KnowledgeBase> route(String question);
}
```

When the sources are only known at runtime (for example, registered by users), use the `DecisionModel` API directly:

```java
DecisionResponse response = decisionModel.decide(DecisionRequest.builder()
        .input(question)
        .question("source", ChoiceQuestion.builder()
                .text("Which knowledge base can answer this question?")
                .options(sources.stream().collect(toMap(Source::name, Source::description)))
                .build())
        .build());

String sourceName = response.choice("source").value();
```
</details>

<details>
<summary>Evaluating answers against a checklist</summary>

Each test case has its own checks, so the questions are only known at runtime.
Use the `DecisionModel` API directly: all checks of a test case are answered in one call.

```java
DecisionRequest.Builder request = DecisionRequest.builder()
        .input(Map.of("question", testCase.question(), "answer", answer));
testCase.checks().forEach(check -> request.question(check.id(), YesNoQuestion.of(check.text())));

DecisionResponse response = decisionModel.decide(request.build());
testCase.checks().forEach(check ->
        assertThat(response.yesNo(check.id()).isYes(0.5)).as(check.text()).isTrue());
```
</details>

<details>
<summary>Reviewing a contract against company policies</summary>

The policies are stored in a database and differ per contract type.
Use the `DecisionModel` API directly, with one yes/no question per policy:

```java
DecisionRequest.Builder request = DecisionRequest.builder().input(contractText);
policies.forEach(policy -> request.question(policy.id(), YesNoQuestion.builder()
        .text("Does the contract comply with this policy?")
        .yesWhen("the contract follows this policy: " + policy.description())
        .build()));

DecisionResponse response = decisionModel.decide(request.build());
List<Policy> violated = policies.stream()
        .filter(policy -> !response.yesNo(policy.id()).isYes(0.8))
        .toList();
```
</details>

<details>
<summary>Checking answers for personal data</summary>

Everything is fixed. The interface can be shared by all AI Services of an application,
for example in an [output guardrail](/tutorials/guardrails).

```java
interface PersonalDataCheck {

    @Decide("""
            Does the text reveal personal data?
            Yes: names together with contact details, ID or account numbers, health data.
            No: public figures, company names, made-up examples.""")
    YesNoAnswer containsPersonalData(String text);
}
```
</details>

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

Results are easy to create in tests: `YesNoAnswer.of(0.97)`, `Choice.builder()`, `Scale.builder(Severity.class)` and `DecisionResult.builder()`.

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
