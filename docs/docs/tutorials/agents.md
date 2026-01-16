---
sidebar_position: 7
---

# Agents and Agentic AI

:::note
This section describes how to build agentic AI applications using the `langchain4j-agentic` module. Please note that the whole module has to be considered experimental and is subject to change in future releases.
:::

## Agentic systems

Although there is no universally agreed definition of an AI agent, several emerging patterns demonstrate how to coordinate and combine the capabilities of multiple AI services to create AI-infused applications that can accomplish more complex tasks. These patterns are often referred to as "agentic systems" or "agentic AI". They typically involve the use of large language models (LLMs) to orchestrate the execution of tasks, manage tool usage, and maintain context across interactions.

According to a [recent article published by Anthropic researchers](https://www.anthropic.com/research/building-effective-agents), these Agentic System architectures can be grouped into two main categories: workflows and pure agents.

![](/img/workflow-vs-agents.png)

The `langchain4j-agentic` module, discussed in this tutorial, provides a set of abstractions and utilities to help you build workflow and pure agentic AI applications. It allows you to define workflows, manage tool usage, and maintain context across interactions with different LLMs.

## Agents in LangChain4j

An agent in LangChain4j performs a specific task or set of tasks using an LLM. An agent can be defined with an interface with a single method, in a similar way to a normal AI service, just adding the `@Agent` annotation to it.

```java
public interface CreativeWriter {

    @UserMessage("""
            You are a creative writer.
            Generate a draft of a story no more than
            3 sentences long around the given topic.
            Return only the story and nothing else.
            The topic is {{topic}}.
            """)
    @Agent("Generates a story based on the given topic")
    String generateStory(@V("topic") String topic);
}
```

It is a good practice to also provide with that annotation a short description of the agent's purpose, especially if it is intended to be used in pure agentic patterns, where other agents need to know the capabilities of this agent to make an informed decision on how and when to use it. This description can be also provided programmatically when building the agent, using the `description` method of the agent builder.

Agents must also have a name uniquely identifying them inside the agentic system. This name can be specified either in the `@Agent` annotation or programmatically using the `name` method of the agent builder. If not specified, the name is taken from the name of the method annotated with `@Agent`.

It is now possible to build an instance of this agent using the `AgenticServices.agentBuilder()` method, specifying the interface and the chat model to use. 

```java
CreativeWriter creativeWriter = AgenticServices
        .agentBuilder(CreativeWriter.class)
        .chatModel(myChatModel)
        .outputKey("story")
        .build();
```

In essence agents are plain AI services, providing the same features, but with the ability to be combined with other agents to create more complex workflows and agentic systems. 

The other main difference with an AI service is the presence of the `outputKey` parameter that is used to specify the name of the shared variable where the result of the agent invocation will be stored in order to make it available for other agents in the same agentic system. Alternatively, the output name can be also declared directly in the `@Agent` annotation instead of programmatically like in this example, so that it could be omitted in the code and added here.

```java
@Agent(outputKey = "story", description = "Generates a story based on the given topic")
```

The `AgenticServices` class provides a set of static factory methods to create and define all kinds of agents made available by the `langchain4j-agentic` framework.

## Introducing the AgenticScope

The langchain4j-agentic module introduces the concept of an `AgenticScope`, which is a collection of data shared among the agents participating in an agentic system. The `AgenticScope` is used to store shared variables, which can be written by an agent to communicate the results it produced and read by another agent to put together the information that it needs to perform its task. This allows agents to collaborate effectively, sharing information and results as needed.

The `AgenticScope` also automatically registers other relevant information like the sequence of invocations of all agents with their responses. It is automatically created when the main agent of the agentic system is invoked and programmatically provided through callbacks when necessary. The different possible usages of the `AgenticScope` will be clarified with practical examples when discussing the agentic patterns implemented by `langchain4j-agentic`.

## Workflow patterns

The `langchain4j-agentic` module provides a set of abstractions to programmatically orchestrate multiple agents and create agentic workflow patterns. These patterns can be combined to create more complex workflows. 

### Sequential workflow

A sequential workflow is the simplest possible pattern where multiple agents are invoked one after the other, with each agent's output being passed as input to the next agent. This pattern is useful when you have a series of tasks that need to be performed in a specific order.

For example, it would be a good idea to complement the `CreativeWriter` agent defined before, with an `AudienceEditor` agent that can edit the generated story to better fit a specific audience

```java
public interface AudienceEditor {

    @UserMessage("""
        You are a professional editor.
        Analyze and rewrite the following story to better align
        with the target audience of {{audience}}.
        Return only the story and nothing else.
        The story is "{{story}}".
        """)
    @Agent("Edits a story to better fit a given audience")
    String editStory(@V("story") String story, @V("audience") String audience);
}
```

and with a very similar `StyleEditor` doing the same job but for a specific style.

```java
public interface StyleEditor {

    @UserMessage("""
        You are a professional editor.
        Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
        Return only the story and nothing else.
        The story is "{{story}}".
        """)
    @Agent("Edits a story to better fit a given style")
    String editStory(@V("story") String story, @V("style") String style);
}
```

Note that the input arguments of this agent are annotated with a variable name. In fact the values of the arguments to be passed to the agent are not provided directly, but rather taken from the `AgenticScope` shared variables having those names. This allows the agent to access the output of previous agents in the workflow. If the agent class is compiled with the `-parameters` option enabled, thus retaining at runtime the names of the method parameters, the `@V` annotation can be omitted, and the variable names will be automatically inferred from the parameter names.

At this point it is possible to create a sequential workflow that combines these three agents, where the output of the `CreativeWriter` is passed as input to both the `AudienceEditor` and `StyleEditor`, and the final output is the edited story.

```java
CreativeWriter creativeWriter = AgenticServices
        .agentBuilder(CreativeWriter.class)
        .chatModel(BASE_MODEL)
        .outputKey("story")
        .build();

AudienceEditor audienceEditor = AgenticServices
        .agentBuilder(AudienceEditor.class)
        .chatModel(BASE_MODEL)
        .outputKey("story")
        .build();

StyleEditor styleEditor = AgenticServices
        .agentBuilder(StyleEditor.class)
        .chatModel(BASE_MODEL)
        .outputKey("story")
        .build();

UntypedAgent novelCreator = AgenticServices
        .sequenceBuilder()
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .outputKey("story")
        .build();

Map<String, Object> input = Map.of(
        "topic", "dragons and wizards",
        "style", "fantasy",
        "audience", "young adults"
);

String story = (String) novelCreator.invoke(input);
```

Here the `novelCreator` agent is actually an agentic system implementing a sequential workflow that combines the three subagents calling them one after the other. Since the definition of this agent hasn't been provided with a typed interface, the sequence agent builder returns an `UntypedAgent` instance, which is a generic agent that can be invoked with an input map. 

```java
public interface UntypedAgent {
    @Agent
    Object invoke(Map<String, Object> input);
}
```

The values in that input map are copied into the `AgenticScope` shared variables, so that they can be accessed by the subagents. The output of the `novelCreator` agent is also taken from the `AgenticScope` shared variable named "story", which has been formerly rewritten by all other agents during the novel creation and editing workflow execution.

Optionally, the workflow agent can also be provided with typed interface, so that it can be invoked with a strongly typed input and output. In this case, the `UntypedAgent` interface can be replaced with a more specific one, like:

```java
public interface NovelCreator {

    @Agent
    String createNovel(@V("topic") String topic, @V("audience") String audience, @V("style") String style);
}
```

so that the `novelCreator` agent can be created and used as follows:

```java
NovelCreator novelCreator = AgenticServices
        .sequenceBuilder(NovelCreator.class)
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .outputKey("story")
        .build();

String story = novelCreator.createNovel("dragons and wizards", "young adults", "fantasy");
```

### Loop workflow

A common way to better leverage the capabilities of LLMs is to use them to iteratively refine a piece of text, like a story, by repeatedly invoking an agent that can edit or improve it. This can be achieved by using a loop workflow pattern, where an agent is invoked multiple times until a certain condition is met.

A `StyleScorer` agent can be used to generate a score based on how well the style aligns with what's required.

```java
public interface StyleScorer {

    @UserMessage("""
            You are a critical reviewer.
            Give a review score between 0.0 and 1.0 for the following
            story based on how well it aligns with the style '{{style}}'.
            Return only the score and nothing else.
            
            The story is: "{{story}}"
            """)
    @Agent("Scores a story based on how well it aligns with a given style")
    double scoreStyle(@V("story") String story, @V("style") String style);
}
```

Then it is possible to use this agent in a loop with the `StyleEditor` one to iteratively improve the story until the score reaches a certain threshold, like 0.8, or until a maximum number of iterations is reached.

```java
StyleEditor styleEditor = AgenticServices
        .agentBuilder(StyleEditor.class)
        .chatModel(BASE_MODEL)
        .outputKey("story")
        .build();

StyleScorer styleScorer = AgenticServices
        .agentBuilder(StyleScorer.class)
        .chatModel(BASE_MODEL)
        .outputKey("score")
        .build();

UntypedAgent styleReviewLoop = AgenticServices
        .loopBuilder()
        .subAgents(styleScorer, styleEditor)
        .maxIterations(5)
        .exitCondition( agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
        .build();
```

Here the `styleScorer` agent writes its output to the `AgenticScope` shared variable named "score", and the same variable is accessed and evaluated in the exit condition of the loop.

The `exitCondition` method takes as argument a `Predicate<AgenticScope>` that by default is evaluated after each and every agent invocation, making the loop to exit as soon as the condition is satisfied, in order to reduce as much as possible the number of agent invocations. However, it is also possible to check the exit condition only at the end of a loop, thus forcing all agents to be invoked before testing that condition, by configuring the loop builder with the `testExitAtLoopEnd(true)` method. Alternatively, the `exitCondition` method can also take as argument a `BiPredicate<AgenticScope, Integer>` that receives as second argument the counter of the current loop iteration. For example, the following loop definition:

```java
UntypedAgent styleReviewLoop = AgenticServices
        .loopBuilder()
        .subAgents(styleScorer, styleEditor)
        .maxIterations(5)
        .testExitAtLoopEnd(true)
        .exitCondition( (agenticScope, loopCounter) -> {
            double score = agenticScope.readState("score", 0.0);
            return loopCounter <= 3 ? score >= 0.8 : score >= 0.6;
        })
        .build();
```

will make the loop to exit if the score is at least 0.8 in the first 3 iterations, otherwise it will lower the quality expectations, terminating the loop with a score of at least 0.6, also forcing the invocation of the `styleEditor` agent one last time even after the exit condition has been satisfied.

After having configured this `styleReviewLoop`, it can be seen as a single agent and put in a sequence with the `CreativeWriter` agent to create a `StyledWriter` agent

```java
public interface StyledWriter {

    @Agent
    String writeStoryWithStyle(@V("topic") String topic, @V("style") String style);
}
```

implementing a more complex workflow that combines the story generation and style review process.

```java
CreativeWriter creativeWriter = AgenticServices
        .agentBuilder(CreativeWriter.class)
        .chatModel(BASE_MODEL)
        .outputKey("story")
        .build();

StyledWriter styledWriter = AgenticServices
        .sequenceBuilder(StyledWriter.class)
        .subAgents(creativeWriter, styleReviewLoop)
        .outputKey("story")
        .build();

String story = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
```

### Parallel workflow

Sometimes it is useful to invoke multiple agents in parallel, especially when they can work independently on the same input. This can be achieved by using a parallel workflow pattern, where multiple agents are invoked simultaneously, and their outputs are combined into a single result.

For example, let's use movie and food experts to generate a few plans for a lovely evening with a specific mood, combining a movie and a meal that matches that mood.

```java
public interface FoodExpert {

    @UserMessage("""
        You are a great evening planner.
        Propose a list of 3 meals matching the given mood.
        The mood is {{mood}}.
        For each meal, just give the name of the meal.
        Provide a list with the 3 items and nothing else.
        """)
    @Agent
    List<String> findMeal(@V("mood") String mood);
}

public interface MovieExpert {

    @UserMessage("""
        You are a great evening planner.
        Propose a list of 3 movies matching the given mood.
        The mood is {mood}.
        Provide a list with the 3 items and nothing else.
        """)
    @Agent
    List<String> findMovie(@V("mood") String mood);
}
```

Since the work of the two experts is independent, it is possible to invoke them in parallel using the `AgenticServices.parallelBuilder()` method, as follows:

```java
FoodExpert foodExpert = AgenticServices
        .agentBuilder(FoodExpert.class)
        .chatModel(BASE_MODEL)
        .outputKey("meals")
        .build();

MovieExpert movieExpert = AgenticServices
        .agentBuilder(MovieExpert.class)
        .chatModel(BASE_MODEL)
        .outputKey("movies")
        .build();

EveningPlannerAgent eveningPlannerAgent = AgenticServices
        .parallelBuilder(EveningPlannerAgent.class)
        .subAgents(foodExpert, movieExpert)
        .executor(Executors.newFixedThreadPool(2))
        .outputKey("plans")
        .output(agenticScope -> {
            List<String> movies = agenticScope.readState("movies", List.of());
            List<String> meals = agenticScope.readState("meals", List.of());

            List<EveningPlan> moviesAndMeals = new ArrayList<>();
            for (int i = 0; i < movies.size(); i++) {
                if (i >= meals.size()) {
                    break;
                }
                moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
            }
            return moviesAndMeals;
        })
        .build();

List<EveningPlan> plans = eveningPlannerAgent.plan("romantic");
```

Here the `output` function of the `AgenticScope` defined in the `EveningPlannerAgent` allows to assemble the outputs of the two subagents, creating a list of `EveningPlan` objects that combine a movie and a meal matching the given mood. The `output` method, even if especially relevant for parallel workflows, can be actually used in any workflow pattern to define how to combine the outputs of the subagents into a single result, instead of simply returning a value from the `AgenticScope`. The `executor` method also allows to optionally provide an `Executor` that will be used to execute the subagents in parallel, otherwise an internal cached thread pool will be used by default.

### Conditional workflow

Another frequent need is to invoke a certain agent only if a specific condition is satisfied. For example, it could be useful to categorize a user request before processing it, so that the processing can be done by different agents depending on the category of the request. This can be achieved by using the following `CategoryRouter`

```java
public interface CategoryRouter {

    @UserMessage("""
        Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
        In case the request doesn't belong to any of those categories categorize it as 'unknown'.
        Reply with only one of those words and nothing else.
        The user request is: '{{request}}'.
        """)
    @Agent("Categorizes a user request")
    RequestCategory classify(@V("request") String request);
}
```

that returns a `RequestCategory` enum value.

```java
public enum RequestCategory {
    LEGAL, MEDICAL, TECHNICAL, UNKNOWN
}
```

In this way, having defined a `MedicalExpert` agent like:

```java
public interface MedicalExpert {

    @UserMessage("""
        You are a medical expert.
        Analyze the following user request under a medical point of view and provide the best possible answer.
        The user request is {{request}}.
        """)
    @Agent("A medical expert")
    String medical(@V("request") String request);
}
```
and similar `LegalExpert` and `TechnicalExpert` agents, it is possible to create a `ExpertRouterAgent`

```java
public interface ExpertRouterAgent {

    @Agent
    String ask(@V("request") String request);
}
```

implementing a conditional workflow that invokes the appropriate agent based on the category of the user request.

```java
CategoryRouter routerAgent = AgenticServices
        .agentBuilder(CategoryRouter.class)
        .chatModel(BASE_MODEL)
        .outputKey("category")
        .build();

MedicalExpert medicalExpert = AgenticServices
        .agentBuilder(MedicalExpert.class)
        .chatModel(BASE_MODEL)
        .outputKey("response")
        .build();
LegalExpert legalExpert = AgenticServices
        .agentBuilder(LegalExpert.class)
        .chatModel(BASE_MODEL)
        .outputKey("response")
        .build();
TechnicalExpert technicalExpert = AgenticServices
        .agentBuilder(TechnicalExpert.class)
        .chatModel(BASE_MODEL)
        .outputKey("response")
        .build();

UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
        .subAgents( agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
        .subAgents( agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
        .subAgents( agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
        .build();

ExpertRouterAgent expertRouterAgent = AgenticServices
        .sequenceBuilder(ExpertRouterAgent.class)
        .subAgents(routerAgent, expertsAgent)
        .outputKey("response")
        .build();

String response = expertRouterAgent.ask("I broke my leg what should I do");
```

## Asynchronous agents

By default, all agents invocations are performed in the same thread that invoked the root agent of the agentic system, and therefore they are synchronous, meaning that the execution of the agentic system waits for the completion of each agent before proceeding to the next one. However, in many cases this is not necessary, and it could be useful to invoke an agent in an asynchronous way, allowing the execution of the agentic system to proceed without waiting for the completion of that agent.

For this reason it is possible to flag an agent as asynchronous using the `async` method of the agent builder. When doing so, the invocation of that agent is performed in a separate thread, and the execution of the agentic system will proceed without waiting for the completion of that agent. The result of the asynchronous agent will be available in the `AgenticScope` as soon as it is completed, and the `AgenticScope` will be blocked waiting for that result only when it is required as an input for a subsequent invocation of a different agent.

For instance, since they are independent of each other, flagging the `FoodExpert` and `MovieExpert` agents, discussed in the parallel workflow section, as asynchronous, will make them to be executed at the same time even when used in a sequential workflow.

```java
FoodExpert foodExpert = AgenticServices
        .agentBuilder(FoodExpert.class)
        .chatModel(BASE_MODEL)
        .async(true)
        .outputKey("meals")
        .build();

MovieExpert movieExpert = AgenticServices
        .agentBuilder(MovieExpert.class)
        .chatModel(BASE_MODEL)
        .async(true)
        .outputKey("movies")
        .build();

EveningPlannerAgent eveningPlannerAgent = AgenticServices
        .sequenceBuilder(EveningPlannerAgent.class)
        .subAgents(foodExpert, movieExpert)
        .executor(Executors.newFixedThreadPool(2))
        .outputKey("plans")
        .output(agenticScope -> {
            List<String> movies = agenticScope.readState("movies", List.of());
            List<String> meals = agenticScope.readState("meals", List.of());

            List<EveningPlan> moviesAndMeals = new ArrayList<>();
            for (int i = 0; i < movies.size(); i++) {
                if (i >= meals.size()) {
                    break;
                }
                moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
            }
            return moviesAndMeals;
        })
        .build();

List<EveningPlan> plans = eveningPlannerAgent.plan("romantic");
```

## Streaming agents

In order to support streaming, it is also possible to create an agent that returns a `TokenStream` 

```java
public interface StreamingCreativeWriter {

    @UserMessage("""
            You are a creative writer.
            Generate a draft of a story no more than
            3 sentences long around the given topic.
            Return only the story and nothing else.
            The topic is {{topic}}.
            """)
    @Agent("Generates a story based on the given topic")
    TokenStream generateStory(@V("topic") String topic);
}
```

and then configure it to use a `StreamingChatModel`, so that the result can be consumed as it is being generated, instead of waiting for the completion of the agent invocation.

```java
StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
        .streamingChatModel(streamingBaseModel())
        .outputKey("story")
        .build();

TokenStream tokenStream = creativeWriter.generateStory("dragons and wizards");
```

When used inside an agentic system, a streaming agent can propagate its streaming response to the whole system only if it is the last agent to be invoked. In all other cases it behaves like an asynchronous agent, so that the subsequent agents would need to wait for the completion of its streaming response to get and use its result.

For example, the following `StreamingReviewedWriter` agent 

```java
public interface StreamingReviewedWriter {
    @Agent
    TokenStream writeStory(@V("topic") String topic, @V("audience") String audience, @V("style") String style);
}
```

is implemented with a sequence of 3 streaming agents

```java
StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
        .streamingChatModel(streamingBaseModel())
        .outputKey("story")
        .build();

StreamingAudienceEditor audienceEditor = AgenticServices.agentBuilder(StreamingAudienceEditor.class)
        .streamingChatModel(streamingBaseModel())
        .outputKey("story")
        .build();

StreamingStyleEditor styleEditor = AgenticServices.agentBuilder(StreamingStyleEditor.class)
        .streamingChatModel(streamingBaseModel())
        .outputKey("story")
        .build();

StreamingReviewedWriter novelCreator = AgenticServices.sequenceBuilder(StreamingReviewedWriter.class)
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .outputKey("story")
        .build();
```

When this `novelCreator` agent is invoked

```java
TokenStream tokenStream = novelCreator.writeStory("dragons and wizards", "young adults", "fantasy");
```

the streaming responses of the first two agents are internally fully consumed before the invocation of the subsequent agents can start, and only the streaming response of the last `StyleEditor` agent is propagated as the streaming response of the whole `novelCreator` agent.

## Error handling

In a complex agentic system, many things can go wrong, such as an agent failing to produce a result, an external tool not being available, or an unexpected error occurring during the execution of an agent.

For this reason, the `errorHandler` method allows to provide the agentic system with an error handler that is a function transforming an `ErrorContext` defined as

```java
record ErrorContext(String agentName, AgenticScope agenticScope, AgentInvocationException exception) { }
```

into an `ErrorRecoveryResult` that can be any of 3 possibilities:

1. `ErrorRecoveryResult.throwException()` which is the default behavior and simply propagates the `Exception` causing the problem up to the root caller
2. `ErrorRecoveryResult.retry()` that retries the agent invocation, possibly after having taken some corrective actions
3. `ErrorRecoveryResult.result(Object result)` that ignores the problems and returns the provided result as outcome of the failing agent.

For instance, if a necessary argument is omitted from the very first example of the sequential workflow

```java
UntypedAgent novelCreator = AgenticServices
        .sequenceBuilder()
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .outputKey("story")
        .build();

Map<String, Object> input = Map.of(
        // missing "topic" entry to trigger an error
        // "topic", "dragons and wizards",
        "style", "fantasy",
        "audience", "young adults"
);
```

the execution will fail with an exception like

```
dev.langchain4j.agentic.agent.MissingArgumentException: Missing argument: topic
```

To solve this problem, in this case it is possible to handle this error and recover from it configuring the agent with an appropriate `errorHandler` that provides the agenticScope with the missing argument as follows.

```java
UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .errorHandler(errorContext -> {
            if (errorContext.agentName().equals("generateStory") &&
                    errorContext.exception() instanceof MissingArgumentException mEx && mEx.argumentName().equals("topic")) {
                errorContext.agenticScope().writeState("topic", "dragons and wizards");
                errorRecoveryCalled.set(true);
                return ErrorRecoveryResult.retry();
            }
            return ErrorRecoveryResult.throwException();
        })
        .outputKey("story")
        .build();
```

## Observability

Tracking and logging the agents' invocations can be crucial for debugging and understanding the aggregate behavior of the whole agentic system in which those agents participate. For this reason, the `langchain4j-agentic` module allows to register an `AgentListener` through the `listener` method of the agent builders, that is notified of all agents invocations and their results, and it is defined as follows:

```java
public interface AgentListener {

    default void beforeAgentInvocation(AgentRequest agentRequest) { }
    default void afterAgentInvocation(AgentResponse agentResponse) { }
    default void onAgentInvocationError(AgentInvocationError agentInvocationError) { }

    default void afterAgenticScopeCreated(AgenticScope agenticScope) { }
    default void beforeAgenticScopeDestroyed(AgenticScope agenticScope) { }

    default void beforeToolExecution(BeforeToolExecution beforeToolExecution) { }
    default void afterToolExecution(ToolExecution toolExecution) { }

    default boolean inheritedBySubagents() {
        return false;
    }
}
```

Note that all methods of this interface have a default empty implementation, so that it is possible to implement only the methods of interest. This will also allow to add new methods in future releases without breaking existing implementations.

For instance the following configuration of the `CreativeWriter` agent will log to the console when it is invoked and what is the story it generated.

```java
CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
        .chatModel(baseModel())
        .outputKey("story")
        .listener(new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                System.out.println("Invoking CreativeWriter with topic: " + request.inputs().get("topic"));
            }
        
            @Override
            public void afterAgentInvocation(AgentResponse response) {
                System.out.println("CreativeWriter generated this story: " + response.output());
            }
        })
        .build();
```

These listener methods receive as argument respectively an `AgentRequest` and an `AgentResponse` that provide useful information about the agent invocation, like its name, the inputs it received and the output it produced, together with the instance of the `AgenticScope` used for that invocation. Note that these methods are invoked in the same thread used to also perform the agent invocation, so they are synchronous with it and should not perform long blocking operations.

`AgentListener`s have 2 important properties, they are:
- **composable**, meaning that you can register multiple listeners to the same agent, by invoking the `listener` method more than once, and they will be notified in the order they were registered; 
- **optionally hierarchical**, meaning that by default they are only local to the agent where they are directly registered, but they can also be inherited by all its subagents, simply making its `inheritedBySubagents` method to return `true`. In that case a listener registered on a top level agent will also be notified of the invocations to all its subagents at any level and composed with all listeners that these subagents could have registered on their own.

### Monitoring

Leveraging the observability features provided by the `AgentListener` interface, the `langchain4j-agentic` module also provides a built-in implementation of this interface, configured to be inherited by all subagents, named `AgentMonitor`, having the goal of recording all agents invocations in an in-memory tree structure, allowing to inspect the sequence of invocations and their results during or after the execution of the agentic system. This monitor can be registered as a listener to the root agent of the agentic system using the `listener` method of the agent builder.

To provide a more comprehensive example, let's reconsider the loop workflow intended to generate and iteratively refine a story until it meets the required style quality, and register a few listeners on it, including an `AgentMonitor`.

```java
AgentMonitor monitor = new AgentMonitor();

CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
        .listener(new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                System.out.println("Invoking CreativeWriter with topic: " + request.inputs().get("topic"));
            }
        })
        .chatModel(baseModel())
        .outputKey("story")
        .build();

StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
        .chatModel(baseModel())
        .outputKey("story")
        .build();

StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
        .name("styleScorer")
        .chatModel(baseModel())
        .outputKey("score")
        .build();

UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
        .subAgents(styleScorer, styleEditor)
        .maxIterations(5)
        .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
        .build();

UntypedAgent styledWriter = AgenticServices.sequenceBuilder()
        .subAgents(creativeWriter, styleReviewLoop)
        .listener(monitor)
        .listener(new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse response) {
                if (response.agentName().equals("styleScorer")) {
                    System.out.println("Current score: " + response.output());
                }
            }
        })
        .outputKey("story")
        .build();
```

Here a first listener is registered directly on the `creativeWriter` agent, so that it logs the request topic for the story to be generated only when that agent is invoked. A second listener is registered on the top level `styledWriter` agent, so that it will be also invoked for all subagents in the hierarchy of that agent at any level. That is why the `afterAgentInvocation` method of that listener checks if the agent being invoked is the `styleScorer`, and only in that case it logs the current score assigned to the style of the generated story.

Finally, the `AgentMonitor` instance is also registered, and automatically composed with the other 2 listeners, as a further listener to the `styledWriter` top level agent, so that it can track all agents invocations in the whole agentic system. 

When invoking the `styledWriter` agent as follows:

```java
Map<String, Object> input = Map.of(
        "topic", "dragons and wizards",
        "style", "comedy");
String story = styledWriter.invoke(input);
```

the `AgentMonitor` records all agents invocations in a tree structure that also keeps track of the start time, finish time, duration, inputs and output of each agent invocation. At this point it is possible to retrieve the recorded executions from the monitor and for instance print it to the console for inspection.

```java
MonitoredExecution execution = monitor.successfulExecutions().get(0);
System.out.println(execution);
```

so it will reveal the nested sequence of agents invocations necessary to generate and refine the story, like it follows:

```
AgentInvocation{agent=Sequential, startTime=2025-12-04T17:23:45.684601233, finishTime=2025-12-04T17:25:31.310476077, duration=105625 ms, inputs={style=comedy, topic=dragons and wiz...}, output=In the shadowy ...}
|=> AgentInvocation{agent=generateStory, startTime=2025-12-04T17:23:45.687031946, finishTime=2025-12-04T17:23:53.216629832, duration=7529 ms, inputs={topic=dragons and wiz...}, output=In the shadowed...}
|=> AgentInvocation{agent=reviewLoop, startTime=2025-12-04T17:23:53.218004760, finishTime=2025-12-04T17:25:31.310442197, duration=98092 ms, inputs={score=0.85, topic=dragons and wiz..., style=comedy, story=In the shadowy ...}, output=null}
    |=> AgentInvocation{agent=scoreStyle, startTime=2025-12-04T17:23:53.218606335, finishTime=2025-12-04T17:23:58.900747685, duration=5682 ms, inputs={style=comedy, story=In the shadowed...}, output=0.25}
    |=> AgentInvocation{agent=editStory, startTime=2025-12-04T17:23:58.901041911, finishTime=2025-12-04T17:24:58.130857588, duration=59229 ms, inputs={style=comedy, story=In the shadowed...}, output=In the shadowy ...}
    |=> AgentInvocation{agent=scoreStyle, startTime=2025-12-04T17:24:58.130980855, finishTime=2025-12-04T17:25:31.310076714, duration=33179 ms, inputs={style=comedy, story=In the shadowy ...}, output=0.85}
```

## Declarative API

All the workflow patterns discussed so far can be defined using a declarative API, which allows you to define workflows in a more concise and readable way. The `langchain4j-agentic` module provides a set of annotations that can be used to define agents and their workflows in a more declarative style.

For instance the `EveningPlannerAgent` implementing the parallel workflow programmatically defined in the previous section can be rewritten using the declarative API as follows:

```java
public interface EveningPlannerAgent {

    @ParallelAgent( outputKey = "plans", 
            subAgents = { FoodExpert.class, MovieExpert.class })
    List<EveningPlan> plan(@V("mood") String mood);

    @ParallelExecutor
    static Executor executor() {
        return Executors.newFixedThreadPool(2);
    }

    @Output
    static List<EveningPlan> createPlans(@V("movies") List<String> movies, @V("meals") List<String> meals) {
        List<EveningPlan> moviesAndMeals = new ArrayList<>();
        for (int i = 0; i < movies.size(); i++) {
            if (i >= meals.size()) {
                break;
            }
            moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
        }
        return moviesAndMeals;
    }
}
```

In this case the static method annotated with `@Output` is used to define how to combine the outputs of the subagents into a single result, exactly in the same way how this has been done passing a function of the `AgenticScope` to the `output` method.

Once this interface is defined, it is possible to create an instance of the `EveningPlannerAgent` using the `AgenticServices.createAgenticSystem()` method, and then use it exactly as before.

```java
EveningPlannerAgent eveningPlannerAgent = AgenticServices
        .createAgenticSystem(EveningPlannerAgent.class, BASE_MODEL);
List<EveningPlan> plans = eveningPlannerAgent.plan("romantic");
```

In this case the `AgenticServices.createAgenticSystem()` method is also provided with a `ChatModel` that by default is used to create all the subagents in this agentic system, However it is also possible to optionally specify a different `ChatModel` for a given subagent, adding to its definition a static method annotated with `@ChatModelSupplier` returning the `ChatModel` to be used with that agent. For instance the `FoodExpert` agent can define its own `ChatModel` as follows:

```java
public interface FoodExpert {

    @UserMessage("""
        You are a great evening planner.
        Propose a list of 3 meals matching the given mood.
        The mood is {{mood}}.
        For each meal, just give the name of the meal.
        Provide a list with the 3 items and nothing else.
        """)
    @Agent(outputKey = "meals")
    List<String> findMeal(@V("mood") String mood);

    @ChatModelSupplier
    static ChatModel chatModel() {
        return FOOD_MODEL;
    }
}
```

In a very similar way, annotating other `static` methods in the agent interface, it is possible to declaratively configure other aspects of the agent like its chat memory, the tools it can use, and so on. Those methods must have no arguments unless differently specified in the following table. The list of annotations available to this purpose follows:

| Annotation Name               | Description                                                                                                                                                   |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@ChatModelSupplier`          | Returns the `ChatModel` to be used by this agent.                                                                                                             |
| `@ChatMemorySupplier`         | Returns the `ChatMemory` to be used by this agent.                                                                                                            |
| `@ChatMemoryProviderSupplier` | Returns the `ChatMemoryProvider` to be used by this agent.<br/>This method requires as argument an `Object` to be used as the memoryId of the created memory. |
| `@ContentRetrieverSupplier`   | Returns the `ContentRetriever` to be used by this agent.                                                                                                      |
| `@AgentListenerSupplier`      | Returns the `AgentListener` to be used by this agent.                                                                                                      |
| `@RetrievalAugmentorSupplier` | Returns the `RetrievalAugmentor` to be used by this agent.                                                                                                    |
| `@ToolsSupplier`              | Returns the tool or set of tools to be used by this agent.<br/> It can return either a single `Object` or a `Object[]`                                        |
| `@ToolProviderSupplier`       | Returns the `ToolProvider` to be used by this agent.                                                                                                          |

To give another example of this declarative API, let's redefine through it the `ExpertsAgent` demonstrated in the conditional workflow section.

```java
public interface ExpertsAgent {

    @ConditionalAgent(outputKey = "response", 
            subAgents = { MedicalExpert.class, TechnicalExpert.class, LegalExpert.class })
    String askExpert(@V("request") String request);

    @ActivationCondition(MedicalExpert.class)
    static boolean activateMedical(@V("category") RequestCategory category) {
        return category == RequestCategory.MEDICAL;
    }

    @ActivationCondition(TechnicalExpert.class)
    static boolean activateTechnical(@V("category") RequestCategory category) {
        return category == RequestCategory.TECHNICAL;
    }

    @ActivationCondition(LegalExpert.class)
    static boolean activateLegal(@V("category") RequestCategory category) {
        return category == RequestCategory.LEGAL;
    }
}
```

In this case the value of the `@ActivationCondition` annotation refers to the set of agents classes that are activated when the method annotated with it returns `true`.

Note that it is also possible to mix the programmatic and declarative styles of defining agents and agentic systems, so that an agent can be configured partially using annotations and partially using the agent builders. It is also allowed to completely define an agent declaratively and then programmatically implement an agentic system using the agent's class as a subagent. For instance, it would be possible to declaratively define the `CreativeWriter` and `AudienceEditor` agents as follows:

```java
public interface CreativeWriter {

    @UserMessage("""
            You are a creative writer.
            Generate a draft of a story long no more than 3 sentence around the given topic.
            Return only the story and nothing else.
            The topic is {{topic}}.
            """)
    @Agent(description = "Generate a story based on the given topic", outputKey = "story")
    String generateStory(@V("topic") String topic);

    @ChatModelSupplier
    static ChatModel chatModel() {
        return baseModel();
    }
}

public interface AudienceEditor {

    @UserMessage("""
        You are a professional editor.
        Analyze and rewrite the following story to better align with the target audience of {{audience}}.
        Return only the story and nothing else.
        The story is "{{story}}".
        """)
    @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
    String editStory(@V("story") String story, @V("audience") String audience);

    @ChatModelSupplier
    static ChatModel chatModel() {
        return baseModel();
    }
}
```

and then programmatically concatenating them in a sequence simply using their classes as subagents.

```java
UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
        .subAgents(CreativeWriter.class, AudienceEditor.class)
        .outputKey("story")
        .build();

Map<String, Object> input = Map.of(
        "topic", "dragons and wizards",
        "audience", "young adults"
);

String story = (String) novelCreator.invoke(input);
```

## Strongly typed inputs and outputs

Up to this point, all input and output keys used to pass data to and from agents have been identified by a simple `String`. However, this approach can be error-prone, as it relies on the correct spelling of those keys. Also in this way it is not possible to strongly bind these variables to a specific types, thus obliging to type checks and casts when reading their values from the `AgenticScope`. To avoid these issues, it is optionally allowed to define strongly typed input and output keys using the `TypedKey` interface.

For instance, following this approach the input and output keys used in the experts routing example discussed when presenting the conditional workflow can be defined as follows:

```java
public static class UserRequest implements TypedKey<String> { }

public static class ExpertResponse implements TypedKey<String> { }

public static class Category implements TypedKey<RequestCategory> {
    @Override
    public Category defaultValue() {
        return Category.UNKNOWN;
    }
}
```

Here both the `UserRequest` and `ExpertResponse` keys are strongly typed as `String`, while the `Category` key is typed as `RequestCategory` enum, and also provides a default value to be used when that key is not present in the `AgenticScope`. Using these typed keys, the `CategoryRouter` agent, used to classify the user's request, can be redefined as follows:

```java
public interface CategoryRouter {

    @UserMessage("""
        Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
        In case the request doesn't belong to any of those categories categorize it as 'unknown'.
        Reply with only one of those words and nothing else.
        The user request is: '{{UserRequest}}'.
        """)
    @Agent(description = "Categorizes a user request", typedOutputKey = Category.class)
    RequestCategory classify(@K(UserRequest.class) String request);
}
```

The argument of the `classify` method is now annotated with the `@K` annotation, indicating that its value must be taken from the `AgenticScope` variable identified by the `UserRequest` typed key. Similarly, the output of this agent is written to the `AgenticScope` variable identified by the `Category` typed key. Note that the prompt template has also been updated to use the name of the typed key, which by default corresponds to the simple name of the class implementing the `TypedKey` interface, `{{UserRequest}}` in this case, but this convention can be overridden also implementing the `name()` method of the `TypedKey` interface. In a similar way, one of the 3 expert agents, the `MedicalExpert` one, can be redefined as follows:

```java
public interface MedicalExpert {

    @UserMessage("""
        You are a medical expert.
        Analyze the following user request under a medical point of view and provide the best possible answer.
        The user request is {{UserRequest}}.
        """)
    @Agent("A medical expert")
    String medical(@K(UserRequest.class) String request);
}
```

At this point it is possible to create the whole agentic system using these typed keys to identify the input and output variables in the `AgenticScope`.

```java
CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
        .chatModel(baseModel())
        .build();

MedicalExpert medicalExpert = AgenticServices.agentBuilder(MedicalExpert.class)
        .chatModel(baseModel())
        .outputKey(ExpertResponse.class)
        .build();
LegalExpert legalExpert = AgenticServices.agentBuilder(LegalExpert.class)
        .chatModel(baseModel())
        .outputKey(ExpertResponse.class)
        .build();
TechnicalExpert technicalExpert = AgenticServices.agentBuilder(TechnicalExpert.class)
        .chatModel(baseModel())
        .outputKey(ExpertResponse.class)
        .build();

UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
        .subAgents(scope -> scope.readState(Category.class) == Category.MEDICAL, medicalExpert)
        .subAgents(scope -> scope.readState(Category.class) == Category.LEGAL, legalExpert)
        .subAgents(scope -> scope.readState(Category.class) == Category.TECHNICAL, technicalExpert)
        .build();

ExpertChatbot expertChatbot = AgenticServices.sequenceBuilder(ExpertChatbot.class)
        .subAgents(routerAgent, expertsAgent)
        .outputKey(ExpertResponse.class)
        .build();

String response = expertChatbot.ask("I broke my leg what should I do");
```

The `routerAgent` doesn't need to programmatically specify the output key, since it is already defined in its interface through the `typedOutputKey` attribute of the `@Agent` annotation, while the 3 expert agents still need to specify it programmatically, since their interfaces don't define it, so as usual it is possible to use either one of the 2 approaches. Also, it worth to note that, when reading the values from the `AgenticScope`, like in the conditional workflow definition, there is no need to perform any type check or cast, since the typed keys already provide the necessary type information.

## Memory and context engineering

All agents discussed so far are stateless, meaning that they do not maintain any context or memory of previous interactions. However, like for any other AI service, it is possible to provide agents with a `ChatMemory`, allowing them to maintain context across multiple invocations. 

To provide the former `MedicalExpert` with a memory, it is sufficient to add a field annotated with `@MemoryId` to its signature.

```java
public interface MedicalExpertWithMemory {

    @UserMessage("""
        You are a medical expert.
        Analyze the following user request under a medical point of view and provide the best possible answer.
        The user request is {{request}}.
        """)
    @Agent("A medical expert")
    String medical(@MemoryId String memoryId, @V("request") String request);
}
```

and set a memory provider when building the agent:

```java
MedicalExpertWithMemory medicalExpert = AgenticServices
        .agentBuilder(MedicalExpertWithMemory.class)
        .chatModel(BASE_MODEL)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
        .outputKey("response")
        .build();
```

Generally this is enough for single agents used in isolation, but can be limiting for agents participating in an agentic system. Supposing that also the technical and legal experts have been provided with a memory, and also the `ExpertRouterAgent` has been redefined to have it:

```java
public interface ExpertRouterAgentWithMemory {

    @Agent
    String ask(@MemoryId String memoryId, @V("request") String request);
}
```

The sequence of these two invocations to this agent

```java
String response1 = expertRouterAgent.ask("1", "I broke my leg, what should I do?");

String legalResponse1 = expertRouterAgent.ask("1", "Should I sue my neighbor who caused this damage?");
```

won't give the expected result, because the second question will be routed to the legal expert, which is now invoked for the first time and has no memory of the previous question.

To solve this problem it is necessary to provide the legal expert with the context and what happened before its invocation, and this is another use case where the information automatically stored in the `AgenticScope` can come to help.

In particular the `AgenticScope` keeps track of the sequence of invocations of all agents, and can produce a context concatenating those invocations in a single conversation. This context can be used as it is or if necessary summarized to a shorter version, for instance defining a `ContextSummarizer` agent.

```java
public interface ContextSummarizer {

    @UserMessage("""
        Create a very short summary, 2 sentences at most, of the
        following conversation between an AI agent and a user.

        The user conversation is: '{{it}}'.
        """)
    String summarize(String conversation);
}
```

Using this agent, the legal expert can be redefined and provided with a context summarization of the previous conversation, so that it can take into account the previous interactions when answering the new question.

```java
LegalExpertWithMemory legalExpert = AgenticServices
        .agentBuilder(LegalExpertWithMemory.class)
        .chatModel(BASE_MODEL)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
        .context(agenticScope -> contextSummarizer.summarize(agenticScope.contextAsConversation()))
        .outputKey("response")
        .build();
```

More in general the context provided to an agent can be any function of the `AgenticScope` state. With this setup, the legal expert, when asked if the neighbor should be sued for the damage he caused, will be able to take into account the previous conversation with the medical expert and provide a more informed answer.

Internally the agentic framework provides the additional context to the legal expert by automatically rewriting the user message sent to it, so that it contains the summarized context of the previous conversation, so in this case the actual user message will be something like:

```
"Considering this context \"The user asked about what to do after breaking their leg, and the AI provided medical advice on immediate actions like immobilizing the leg, applying ice, and seeking medical attention.\"
You are a legal expert.
Analyze the following user request under a legal point of view and provide the best possible answer.
The user request is Should I sue my neighbor who caused this damage?."
```

The summarized context discussed here as an example of possible context generation for an agent is of general usefulness, so it is possible to define it on an agent in a more convenient way, using the `summarizedContext` method, like in:

```java
LegalExpertWithMemory legalExpert = AgenticServices
        .agentBuilder(LegalExpertWithMemory.class)
        .chatModel(BASE_MODEL)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
        .summarizedContext("medical", "technical")
        .outputKey("response")
        .build();
```

By doing so it internally uses the `ContextSummarizer` agent discussed before, executing it with the same chat model of the agent where it has been defined. It is also possible to add to this method a varargs of the names of the agents whose context should be summarized, so that the summarization is done only for those agents, and not for all the ones used in the agentic system.

### AgenticScope registry and persistence

The `AgenticScope` is a transient data structure that is created and used during the execution of an agentic system. There is a single `AgenticScope` per user per agentic system. For stateless executions, when no memory is used, the `AgenticScope` is automatically discarded at the end of the execution, and its state is not persisted anywhere. 

Conversely, when the agentic system uses a memory, the `AgenticScope` is saved in an internal registry. In this case the `AgenticScope` remains in the registry forever to allow users to interact with the agentic system in a stateful and conversational way. For this reason, when a `AgenticScope` with a specific ID is no longer needed, it has to be explicitly evicted from the registry. In order to do so the root agent of the agentic system needs to implement the interface `AgenticScopeAccess` so it is possible to call the `evictAgenticScope` method on it, passing the ID of the `AgenticScope` that has to be removed from the registry.:

```java
agent.evictAgenticScope(memoryId);
```

Both the `AgenticScope`s and their registry are purely in memory data structures. This is usually sufficient for simple agentic systems, but in some cases it can be useful to persist the `AgenticScope` state to a more durable storage, like a database or a file system. To achieve this the `langchain4j-agentic` module provides an SPI to plug in a custom persistence layer that is an implementation of the `AgenticScopeStore` interface. It is possible to set this persistence layer either programmatically:

```java
AgenticScopePersister.setStore(new MyAgenticScopeStore());
```

or using the standard Java Service Provider interface creating a file named `META-INF/services/dev.langchain4j.agentic.scope.AgenticScopeStore` containing the fully qualified name of the class implementing the `AgenticScopeStore` interface.

## Pure agentic AI

Up to this point all agents have been wired and combined to create agentic systems using deterministic workflows. However, there are cases where the agentic system needs to be more flexible and adaptive, allowing agents to make decisions on how to proceed based on the context and the results of previous interactions. This is often referred to as "pure agentic AI".

To this purpose, the `langchain4j-agentic` module provides out-of-the-box a supervisor agent that can be provided with a set of subagents and can autonomously generate a plan, deciding which agent to invoke next or if the assigned task has been completed.

To provide an example of how this works let's define a few agents that can credit or withdraw money from a bank account or exchange a given amount from one currency to another.

```java
public interface WithdrawAgent {

    @SystemMessage("""
            You are a banker that can only withdraw US dollars (USD) from a user account,
            """)
    @UserMessage("""
            Withdraw {{amount}} USD from {{user}}'s account and return the new balance.
            """)
    @Agent("A banker that withdraw USD from an account")
    String withdraw(@V("user") String user, @V("amount") Double amount);
}

public interface CreditAgent {
    @SystemMessage("""
        You are a banker that can only credit US dollars (USD) to a user account,
        """)
    @UserMessage("""
        Credit {{amount}} USD to {{user}}'s account and return the new balance.
        """)
    @Agent("A banker that credit USD to an account")
    String credit(@V("user") String user, @V("amount") Double amount);
}

public interface ExchangeAgent {
    @UserMessage("""
            You are an operator exchanging money in different currencies.
            Use the tool to exchange {{amount}} {{originalCurrency}} into {{targetCurrency}}
            returning only the final amount provided by the tool as it is and nothing else.
            """)
    @Agent("A money exchanger that converts a given amount of money from the original to the target currency")
    Double exchange(@V("originalCurrency") String originalCurrency, @V("amount") Double amount, @V("targetCurrency") String targetCurrency);
}
```

All these agents use external tools to perform their tasks, specifically a `BankTool` that can be used to withdraw or credit money from users' accounts

```java
public class BankTool {

    private final Map<String, Double> accounts = new HashMap<>();

    void createAccount(String user, Double initialBalance) {
        if (accounts.containsKey(user)) {
            throw new RuntimeException("Account for user " + user + " already exists");
        }
        accounts.put(user, initialBalance);
    }

    double getBalance(String user) {
        Double balance = accounts.get(user);
        if (balance == null) {
            throw new RuntimeException("No balance found for user " + user);
        }
        return balance;
    }

    @Tool("Credit the given user with the given amount and return the new balance")
    Double credit(@P("user name") String user, @P("amount") Double amount) {
        Double balance = accounts.get(user);
        if (balance == null) {
            throw new RuntimeException("No balance found for user " + user);
        }
        Double newBalance = balance + amount;
        accounts.put(user, newBalance);
        return newBalance;
    }

    @Tool("Withdraw the given amount with the given user and return the new balance")
    Double withdraw(@P("user name") String user, @P("amount") Double amount) {
        Double balance = accounts.get(user);
        if (balance == null) {
            throw new RuntimeException("No balance found for user " + user);
        }
        Double newBalance = balance - amount;
        accounts.put(user, newBalance);
        return newBalance;
    }
}
```

and an `ExchangeTool` that can be used to exchange money from one currency to another, perhaps using a REST service providing the most updated exchange rate.

```java
public class ExchangeTool {

    @Tool("Exchange the given amount of money from the original to the target currency")
    Double exchange(@P("originalCurrency") String originalCurrency, @P("amount") Double amount, @P("targetCurrency") String targetCurrency) {
        // Invoke a REST service to get the exchange rate
    }
}
```

It is now possible to create instances of these agents as usual using the `AgenticServices.agentBuilder()` method, configure them to use these tools, and then use them as subagents of the supervisor agent.

```java
BankTool bankTool = new BankTool();
bankTool.createAccount("Mario", 1000.0);
bankTool.createAccount("Georgios", 1000.0);

WithdrawAgent withdrawAgent = AgenticServices
        .agentBuilder(WithdrawAgent.class)
        .chatModel(BASE_MODEL)
        .tools(bankTool)
        .build();
CreditAgent creditAgent = AgenticServices
        .agentBuilder(CreditAgent.class)
        .chatModel(BASE_MODEL)
        .tools(bankTool)
        .build();

ExchangeAgent exchangeAgent = AgenticServices
        .agentBuilder(ExchangeAgent.class)
        .chatModel(BASE_MODEL)
        .tools(new ExchangeTool())
        .build();

SupervisorAgent bankSupervisor = AgenticServices
        .supervisorBuilder()
        .chatModel(PLANNER_MODEL)
        .subAgents(withdrawAgent, creditAgent, exchangeAgent)
        .responseStrategy(SupervisorResponseStrategy.SUMMARY)
        .build();
```

Note that the subagents can also be complex agents implementing a workflow, that will be seen as a single agent by the supervisor.

The resulting `SupervisorAgent` typically takes a user request as input and produces a response, so its signature is simply as follows:

```java
public interface SupervisorAgent {
    @Agent
    String invoke(@V("request") String request);
}
```

Now let's suppose to invoke this agent with the following request:

```java
bankSupervisor.invoke("Transfer 100 EUR from Mario's account to Georgios' one")
```

What happens internally is that the supervisor agent will analyze the request and generate a plan to accomplish the task, made by a sequence of `AgentInvocation`s

```java
public record AgentInvocation(String agentName, Map<String, String> arguments) {}
```

For example, for the former request the supervisor could generate a sequence of invocations like the following:

```
AgentInvocation{agentName='exchange', arguments={originalCurrency=EUR, amount=100, targetCurrency=USD}}

AgentInvocation{agentName='withdraw', arguments={user=Mario, amount=115.0}}

AgentInvocation{agentName='credit', arguments={user=Georgios, amount=115.0}}

AgentInvocation{agentName='done', arguments={response=The transfer of 100 EUR from Mario's account to Georgios' account has been completed. Mario's balance is 885.0 USD, and Georgios' balance is 1115.0 USD. The conversion rate was 1.15 EUR to USD.}}
```

The last invocation is a special one that signals the supervisor believes the task has been completed, and returns as a response a summary of all the operations performed.

In many cases, like this one, this summary is the final response that should be returned to the user, but not always. Suppose that you use the `SupervisorAgent` instead of a plain sequence workflow to create a story and edit it according to a given style and audience as in the very first example. In this case the user will be interested only in the final story, and not in a summary of the intermediate steps taken to create it.

Returning the response generated by the last invoked agent, instead of the summary, is actually the most common scenario so this is also the default behavior of the supervisor agent. For this situation however returning the summary of all the performed transactions is more appropriate, so that the `SupervisorAgent` has been configured accordingly through that `responseStrategy` method.

The next section discusses this and other possible customizations of the supervisor agent.

### Supervisor design and customization

More in general there could be cases where it is not possible to know in advance which of the two responses, the summary generated by the supervisor and the last response of the last invoked agent, is the most appropriate one to be returned. For these situation it has been made available a second agent that is passed with those two possible responses together with the original user request, and that scores them to decide which one fits better the request and then which one to return. 

The `SupervisorResponseStrategy` enum make it possible to enable this scorer agent or to always return one of the two responses skipping the scoring process.

```java
public enum SupervisorResponseStrategy {
    SCORED, SUMMARY, LAST
}
```

As anticipated, the default behavior is `LAST` and the other strategy implementations can be configured on the supervisor agent using the `responseStrategy` method.

```java
AgenticServices.supervisorBuilder()
        .responseStrategy(SupervisorResponseStrategy.SCORED)
        .build();
```

For instance using the `SCORED` strategy in the banking example, it could produce the following response scores:

```
ResponseScore{finalResponse=0.3, summary=1.0}
```

thus making the supervisor agent to return the summary as the final response to the user request.

The architecture of the supervisor agent as it has been described so far is shown in the following diagram:

![](/img/supervisor.png)

The information used by the supervisor to decide the next action to take are another of its key aspect. By default, the supervisor simply uses the local chat memory, but in some cases it can be useful to provide it with a more comprehensive context, generated by summarizing the conversations of its subagents, in a very similar way to what has been discussed in the section on context engineering, or even to combine both approaches at the same time. The 3 possibilities are represented by the following enum:

```java
public enum SupervisorContextStrategy {
    CHAT_MEMORY, SUMMARIZATION, CHAT_MEMORY_AND_SUMMARIZATION
}
```

that can be set when building the supervisor agent using the `contextGenerationStrategy` method:

```java
AgenticServices.supervisorBuilder()
        .contextGenerationStrategy(SupervisorContextStrategy.SUMMARIZATION)
        .build();
```

Other customization points for the supervisor agent could be eventually implemented and made available in the future.

### Providing context to the supervisor

In many real-world scenarios, the supervisor benefits from an optional context: constraints, policies, or preferences that should guide planning (for example, "prefer internal tools", "do not call external services", "currency must be USD", etc.).

This context is stored in the `AgenticScope`, variable named `supervisorContext`. You can provide it in two ways:

- Build-time configuration:

```java
SupervisorAgent bankSupervisor = AgenticServices
        .supervisorBuilder()
        .chatModel(PLANNER_MODEL)
        .supervisorContext("Policies: prefer internal tools; currency USD; no external APIs")
        .subAgents(withdrawAgent, creditAgent, exchangeAgent)
        .responseStrategy(SupervisorResponseStrategy.SUMMARY)
        .build();
```

- Invocation (typed supervisor): add a parameter annotated with `@V("supervisorContext")`:

```java
public interface SupervisorAgent {
    @Agent
    String invoke(@V("request") String request, @V("supervisorContext") String supervisorContext);
}

// Example call (overrides the build-time value for this invocation)
bankSupervisor.invoke(
        "Transfer 100 EUR from Mario's account to Georgios' one",
        "Policies: convert to USD first; use bank tools only; no external APIs"
);
```

- Invocation (untyped supervisor): set `supervisorContext` in the input map:

```java
Map<String, Object> input = Map.of(
        "request", "Transfer 100 EUR from Mario's account to Georgios' one",
        "supervisorContext", "Policies: convert to USD first; use bank tools only; no external APIs"
);

String result = (String) bankSupervisor.invoke(input);
```

If both are provided, the invocation value overrides the build-time `supervisorContext`.

## Custom agentic patterns

The agentic patterns discussed so far are provided out-of-the-box by the `langchain4j-agentic` module, but what if none of them fit the specific needs of your application? In this case it is possible to create your own custom pattern, that orchestrates the interactions among a set of subagents in a way that is tailored to your requirements.

In more details an agentic pattern is simply the specification of an execution plan for the subagents that it coordinates. This plan can be defined by implementing the following `Planner` interface:

```java
public interface Planner {

    default void init(InitPlanningContext initPlanningContext) { }

    default Action firstAction(PlanningContext planningContext) {
        return nextAction(planningContext);
    }

    Action nextAction(PlanningContext planningContext);
}
```

This interface has three methods: `init`, `firstAction`, and `nextAction`. The `init` method is called once at the beginning of the execution, and can be used to initialize any state or data structures needed by the planner. The `firstAction` method is called to determine the first action to be taken by the agentic pattern, while the `nextAction` method is called after each agent execution to determine the next action to be taken based on the current state of the `AgenticScope` and the result of the previous agent execution.

Note that the `firstAction` method has been introduced only because in many cases it is convenient to have a distinct callback defining the very first agent to be invoked by the `Planner`. However, for the situations where this distinction is not necessary, it provides a default implementation that simply forwards the call to the `nextAction` method, so it is not strictly necessary to override it.

The `Action` class returned by the `firstAction` and `nextAction` methods represents the next step to be taken by the agentic pattern, and can be one either a list of one or more subagents to be called next, or a signal that the execution has been completed. If the action specifies only one subagent invocation, then it will be executed sequentially, and in the same thread that is executing the planner itself, while if there are more than one, they will be executed in parallel using the provided `Executor` or the LangChain4j default one.

All the built-in agentic patterns are also written in terms of this `Planner` abstraction and giving a look at their implementation can clarify how this works and be a good starting point to create your own custom patterns. For instance the parallel workflow is probably the simplest of those implementation, and it is defined as follows:

```java
public class ParallelPlanner implements Planner {

    private List<AgentInstance> agents;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        return call(agents);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return done();
    }
}
```

Here the `init` method simply stores the list of subagents with which the parallel workflow has been configured, while the `firstAction` method returns an action that calls all those agents in parallel. Once this parallel execution is completed, there isn't any other action to be taken, so the `nextAction` method simply returns `done()` used to signal the termination of the execution.

The `Planner` implementing the sequential workflow is only slightly more complex, as it needs to keep track of the next subagent to be invoked using an internal cursor, and then return the appropriate action in the `nextAction` method or signal the termination of the execution when all subagents have been invoked.

```java
public class SequentialPlanner implements Planner {

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return agentCursor >= agents.size() ? done() : call(agents.get(agentCursor++));
    }
}
```

To understand how to define an agentic system from a planner implementation, it is possible, for example, to create an instance of the formerly discussed sequential workflow generating a novel for a topic and then editing it for a specific style and audience, as it follows:

```java
UntypedAgent novelCreator = AgenticServices.plannerBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .planner(SequentialPlanner::new)
                .build();
```

which is totally equivalent to use the dedicated API for sequential workflows:

```java
UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();
```

The `plannerBuilder()` method is similar to all other agent builders, with the only difference that it requires to provide a `Supplier<Planner>` returning a new instance of the specific planner to be used by this agentic system. Of course an agentic system implementing a custom planner can be seamlessly combined with any other of the agentic pattern offered out-of-the-box by the `langchain4j-agentic` module.

Having clarified how this `Planner` abstraction works, it is now possible to create your own custom agentic patterns by implementing it. The following sections discuss the two examples of custom patterns, provided in the `langchain4j-agentic-patterns` module, that can be useful in different scenarios. Other custom patterns can be created following the same approach and may be contributed back to the LangChain4j project.

### Goal oriented agentic pattern

The workflow patterns and the supervisor agent represents the two extremes of the spectrum of possible agentic systems: the former is completely deterministic and rigid, forcing to decide in advance the sequence of agents to be invoked, while the latter is completely flexible and adaptive, but delegates the decision of the sequence of agents to be invoked to a non-deterministic LLM. However, there are cases where a middle ground between these two extremes can be more appropriate, allowing agents to work towards a specific goal in a relatively flexible way, but also determining how these agents should be invoked in an algorithmic way.

In order to put this approach in practice, not only the whole agentic system needs to define a goal, but also each subagent needs to declare its own pre and postconditions. This is necessary to calculate the sequence of agent invocations that lead to the achievement of the goal in the fastest possible way. However, all this information are implicitly already present in the agentic system, as those pre and postconditions are nothing else than the required inputs and produced outputs of each agent, and the final goal is simply the desired outputs of the whole agentic system.

Following this idea, it is possible to calculate a dependency graph of all the subagents participating in the agentic system, and then to implement a `Planner` that is capable of analyzing the initial state of the `AgenticScope`, comparing it with the desired goal, and then using that graph to determine the sequence of agent invocations that can lead to the achievement of that goal.

```java
public class GoalOrientedPlanner implements Planner {

    private String goal;

    private GoalOrientedSearchGraph graph;
    private List<AgentInstance> path;

    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.goal = initPlanningContext.plannerAgent().outputKey();
        this.graph = new GoalOrientedSearchGraph(initPlanningContext.subagents());
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        path = graph.search(planningContext.agenticScope().state().keySet(), goal);
        if (path.isEmpty()) {
            throw new IllegalStateException("No path found for goal: " + goal);
        }
        return call(path.get(agentCursor++));
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return agentCursor >= path.size() ? done() : call(path.get(agentCursor++));
    }
}
```

As anticipated, here the goal coincides with the final output of the planner-based agentic pattern itself, while the path from the initial state to the goal is calculated using a `GoalOrientedSearchGraph`, that is built analyzing the input and output keys of all subagents. The sequence of agents to be invoked is then calculated as the shortest path on that graph from the current state to the desired goal.

To give a practical example of how this works, let's try to build a goal-oriented agentic system that can extract the name and zodiac sign of a person from a prompt, generate the horoscope for that sign, look for a related story on the internet and finally create a nice writeup combining all this information. We can achieve this set of tasks by using the following 5 agents:

```java
public interface HoroscopeGenerator {
    @SystemMessage("You are an astrologist that generates horoscopes based on the user's name and zodiac sign.")
    @UserMessage("Generate the horoscope for {{person}} who is a {{sign}}.")
    @Agent("An astrologist that generates horoscopes based on the user's name and zodiac sign.")
    String horoscope(@V("person") Person person, @V("sign") Sign sign);
}

public interface PersonExtractor {

    @UserMessage("Extract a person from the following prompt: {{prompt}}")
    @Agent("Extract a person from user's prompt")
    Person extractPerson(@V("prompt") String prompt);
}

public interface SignExtractor {

    @UserMessage("Extract the zodiac sign of a person from the following prompt: {{prompt}}")
    @Agent("Extract a person from user's prompt")
    Sign extractSign(@V("prompt") String prompt);
}

public interface Writer {
    @UserMessage("""
            Create an amusing writeup for {{person}} based on the following:
            - their horoscope: {{horoscope}}
            - a current news story: {{story}}
            """)
    @Agent("Create an amusing writeup for the target person based on their horoscope and current news stories")
    String write(@V("person") Person person, @V("horoscope") String horoscope, @V("story") String story);
}

public interface StoryFinder {

    @SystemMessage("""
            You're a story finder, use the provided web search tools, calling it once and only once,
            to find a fictional and funny story on the internet about the user provided topic.
            """)
    @UserMessage("""
            Find a story on the internet for {{person}} who has the following horoscope: {{horoscope}}.
            """)
    @Agent("Find a story on the internet for a given person with a given horoscope")
    String findStory(@V("person") Person person, @V("horoscope") String horoscope);
}
```

Leveraging the `GoalOrientedPlanner` developed before, these agents can be combined in a goal-oriented agentic system as follows:

```java
HoroscopeGenerator horoscopeGenerator = AgenticServices.agentBuilder(HoroscopeGenerator.class)
        .chatModel(baseModel())
        .outputKey("horoscope")
        .build();

PersonExtractor personExtractor = AgenticServices.agentBuilder(PersonExtractor.class)
        .chatModel(baseModel())
        .outputKey("person")
        .build();

SignExtractor signExtractor = AgenticServices.agentBuilder(SignExtractor.class)
        .chatModel(baseModel())
        .outputKey("sign")
        .build();

Writer writer = AgenticServices.agentBuilder(Writer.class)
        .chatModel(baseModel())
        .outputKey("writeup")
        .build();

StoryFinder storyFinder = AgenticServices.agentBuilder(StoryFinder.class)
        .chatModel(baseModel())
        .tools(new WebSearchTool())
        .outputKey("story")
        .build();

UntypedAgent horoscopeAgent = AgenticServices.plannerBuilder()
        .subAgents(horoscopeGenerator, personExtractor, signExtractor, writer, storyFinder)
        .outputKey("writeup")
        .planner(GoalOrientedPlanner::new)
        .build();
```

As anticipated, the overall goal of this agentic system is to produce a `writeup` which is also the output key of the GOAP-based planner itself. Taking into account the inputs and outputs of all subagents, the dependency graph built by the `GoalOrientedSearchGraph` will look like this:

![](/img/goap.png)

When invoking this agentic system with a prompt like "My name is Mario and my zodiac sign is pisces"

```java
Map<String, Object> input = Map.of("prompt", "My name is Mario and my zodiac sign is pisces");
String writeup = horoscopeAgent.invoke(input);
```

the `GoalOrientedPlanner` will analyze the initial state of the `AgenticScope`, that contains only the `prompt` variable, and then it will calculate the shortest path on the dependency graph from that initial state to the desired goal, which is the `writeup`, so that the resulting sequence of agent invocations will be:

```
Agents path sequence: [extractPerson, extractSign, horoscope, findStory, write]
```

Note that, as anticipated, this goal-oriented agentic pattern can be mixed and combined with any other of the existing agentic patterns. For instance this possibility can be used to overcome an evident limitation of this approach that, being optimized to reach a specific goal following the shortest possible path, structurally doesn't allow loops, so in some cases it could be useful to have a loop agentic pattern as a subagent of this goal-oriented one.

### Peer-to-peer agentic pattern

All the agentic system discussed up to this point are based on a centralized and hierarchical architecture. In fact all the workflow patterns had a well-defined top-level agent coordinating the activities of multiple sub-agents in a programmatically predetermined way. Even the supervisor pattern, which is more flexible and dynamic thanks to the presence of its LLM-based planner agent, still relies on a coordinator agent that controls the interactions among the various sub-agents. This typology of architectures are suitable for many applications and scenarios, but they can also have some limitations, especially in terms of scalability and fault tolerance. This is why we may want to offer an alternative peer-to-peer approach for multi-agent systems, that can overcome these limitations by adopting a more decentralized and distributed strategy.

In a peer-to-peer agentic systems there isn't any top level agent, and all agents are equal peers that are coordinated through the state of the `AgenticScope`. In particular, an agent is triggered by the presence of its own required inputs as state variables in the `AgenticScope`. Subsequently, a change in one or more of those variables, produced by the output of a different agent, can retrigger the invocation of that agent again. The process terminates either when the `AgenticScope` reaches a stable state and no agent can be invoked anymore, or when the predefined exit condition is satisfied, or when a maximum number of agent invocations has been reached. A `Planner` implementation that realizes this peer-to-peer agentic pattern could be written as it follows:

```java
public class P2PPlanner implements Planner {

    private final int maxAgentsInvocations;
    private final BiPredicate<AgenticScope, Integer> exitCondition;

    private int invocationCounter = 0;
    private Map<String, AgentActivator> agentActivators;

    public P2PPlanner(int maxAgentsInvocations, BiPredicate<AgenticScope, Integer> exitCondition) {
        this(null, maxAgentsInvocations, exitCondition);
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agentActivators = initPlanningContext.subagents().stream().collect(toMap(AgentInstance::agentId, AgentActivator::new));
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        if (terminated(planningContext.agenticScope())) {
            return done();
        }

        AgentActivator lastExecutedAgent = agentActivators.get(planningContext.previousAgentInvocation().agentId());
        lastExecutedAgent.finishExecution();
        agentActivators.values().forEach(a -> a.onStateChanged(lastExecutedAgent.agent.outputKey()));

        return nextCallAction(planningContext.agenticScope());
    }

    private Action nextCallAction(AgenticScope agenticScope) {
        AgentInstance[] agentsToCall = agentActivators.values().stream()
                .filter(agentActivator -> agentActivator.canActivate(agenticScope))
                .peek(AgentActivator::startExecution)
                .map(AgentActivator::agent)
                .toArray(AgentInstance[]::new);
        invocationCounter += agentsToCall.length;
        return call(agentsToCall);
    }

    private boolean terminated(AgenticScope agenticScope) {
        return invocationCounter > maxAgentsInvocations || exitCondition.test(agenticScope, invocationCounter);
    }
}
```

Here the `P2PPlanner` keeps track of the number of agent invocations performed so far, and uses an `AgentActivator` for each subagent to determine if it can be invoked based on the current state of the `AgenticScope`. The `nextAction` method checks if the exit condition has been met or if the maximum number of invocations has been reached, and if not, it identifies all agents that can be activated based on the current state, marks them as started, and returns an action to call them.

To give a practical example of how this works let's try to build a peer-to-peer agentic system that can perform a scientific research and formulate new hypothesis on a given topic, so that the API of this service could be something like:

```java
public interface ResearchAgent {

    @Agent("Conduct research on a given topic")
    String research(@V("topic") String topic);
}
```

To this purpose the following 5 agents can be defined:

```java
public interface LiteratureAgent {

    @SystemMessage("Search for scientific literature on the given topic and return a summary of the findings.")
    @UserMessage("""
            You are a scientific literature search agent.
            Your task is to find relevant scientific papers on the topic provided by the user and summarize them.
            Use the provided tool to search for scientific papers and return a summary of your findings.
            The topic is: {{topic}}
            """)
    @Agent("Search for scientific literature on a given topic")
    String searchLiterature(@V("topic") String topic);
}

public interface HypothesisAgent {

    @SystemMessage("Based on the research findings, formulate a clear and concise hypothesis related to the given topic.")
    @UserMessage("""
            You are a hypothesis formulation agent.
            Your task is to formulate a clear and concise hypothesis based on the research findings provided by the user.
            The topic is: {{topic}}
            The research findings are: {{researchFindings}}
            """)
    @Agent("Formulate hypothesis around a give topic based on research findings")
    String makeHypothesis(@V("topic") String topic, @V("researchFindings") String researchFindings);
}

public interface CriticAgent {

    @SystemMessage("Critically evaluate the given hypothesis related to the specified topic. Provide constructive feedback and suggest improvements if necessary.")
    @UserMessage("""
            You are a critical evaluation agent.
            Your task is to critically evaluate the hypothesis provided by the user in relation to the specified topic.
            Provide constructive feedback and suggest improvements if necessary.
            If you need to, you can also perform additional research to validate or confute the hypothesis using the provided tool.
            The topic is: {{topic}}
            The hypothesis is: {{hypothesis}}
            """)
    @Agent("Critically evaluate a hypothesis related to a given topic")
    String criticHypothesis(@V("topic") String topic, @V("hypothesis") String hypothesis);
}

public interface ValidationAgent {

    @SystemMessage("Validate the provided hypothesis on the given topic based on the critique provided.")
    @UserMessage("""
            You are a validation agent.
            Your task is to validate the hypothesis provided by the user in relation to the specified topic based on the critique provided.
            Validate the provided hypothesis, either confirming it or reformulating a different hypothesis based on the critique.
            The topic is: {{topic}}
            The hypothesis is: {{hypothesis}}
            The critique is: {{critique}}
            """)
    @Agent("Validate a hypothesis based on a given topic and critique")
    String validateHypothesis(@V("topic") String topic, @V("hypothesis") String hypothesis, @V("critique") String critique);
}

public interface ScorerAgent {

    @SystemMessage("Score the provided hypothesis on the given topic based on the critique provided.")
    @UserMessage("""
            You are a scoring agent.
            Your task is to score the hypothesis provided by the user in relation to the specified topic based on the critique provided.
            Score the provided hypothesis on a scale from 0.0 to 1.0, where 0.0 means the hypothesis is completely invalid and 1.0 means the hypothesis is fully valid.
            The topic is: {{topic}}
            The hypothesis is: {{hypothesis}}
            The critique is: {{critique}}
            """)
    @Agent("Score a hypothesis based on a given topic and critique")
    double scoreHypothesis(@V("topic") String topic, @V("hypothesis") String hypothesis, @V("critique") String critique);
}
```

These agents will be all provided with a tool capable of performing research on scientific literature, for instance downloading academic papers from arXiv, and then added to the P2P agentic system:

```java
ArxivCrawler arxivCrawler = new ArxivCrawler();

LiteratureAgent literatureAgent = AgenticServices.agentBuilder(LiteratureAgent.class)
        .chatModel(baseModel())
        .tools(arxivCrawler)
        .outputKey("researchFindings")
        .build();
HypothesisAgent hypothesisAgent = AgenticServices.agentBuilder(HypothesisAgent.class)
        .chatModel(baseModel())
        .tools(arxivCrawler)
        .outputKey("hypothesis")
        .build();
CriticAgent criticAgent = AgenticServices.agentBuilder(CriticAgent.class)
        .chatModel(baseModel())
        .tools(arxivCrawler)
        .outputKey("critique")
        .build();
ValidationAgent validationAgent = AgenticServices.agentBuilder(ValidationAgent.class)
        .chatModel(baseModel())
        .tools(arxivCrawler)
        .outputKey("hypothesis")
        .build();
ScorerAgent scorerAgent = AgenticServices.agentBuilder(ScorerAgent.class)
        .chatModel(baseModel())
        .tools(arxivCrawler)
        .outputKey("score")
        .build();

ResearchAgent researcher = AgenticServices.plannerBuilder(ResearchAgent.class)
        .subAgents(literatureAgent, hypothesisAgent, criticAgent, validationAgent, scorerAgent)
        .outputKey("hypothesis")
        .planner(() -> new P2PPlanner(10, agenticScope -> {
            if (!agenticScope.hasState("score")) {
                return false;
            }
            double score = agenticScope.readState("score", 0.0);
            System.out.println("Current hypothesis score: " + score);
            return score >= 0.85;
        }))
        .build();

String hypothesis = researcher.research("black holes");
```

With this configuration the `researcher` p2p coordinator is passed with the topic of the research. At this point the only agent that can be invoked is the `literatureAgent`, because it is the only one that has all its required inputs, in this case the `topic`, present in the `AgenticScope`. The invocation of this agent produces the `researchFindings` variable, which is added to the `AgenticScope` state, and this new variable triggers the invocation of the `HypothesisAgent`. Then this produces a `hypothesis` that in turn triggers the `criticAgent`. Finally, the `ValidationAgent` takes in input both the `hypothesis` and the `critique` and generates a new `hypothesis` that eventually retriggers the other agents again. In the meanwhile the `ScorerAgent` gives a `score` to the `hypothesis` and the process terminates when this `score` is greater than or equal to 0.85, or when a maximum of 10 agents invocations have been performed. The following image summarizes all the agents and variables involved in this execution.

![](/img/p2p.png)

For instance a typical run of this example could terminate because the `ScorerAgent` produced a score above the predetermined threshold

```
Current hypothesis score: 0.95
```

and the final output could be something like:

```
Based on the provided references, here are some key points about stochastic gravitational wave backgrounds (SGWBs) from primordial black holes (PBHs):

1. **Detection Rates and Sources:**
   - The detection rate of gravity waves emitted during parabolic encounters of stellar black holes in globular clusters was estimated by Kocsis et al. [85].
   - Gravitational wave bursts from PBH hyperbolic encounters were discussed by García-Bellido and Nesseris [93].

2. **Energy Emission:**
   - The energy spectrum of gravitational waves from hyperbolic encounters was studied by De Vittori, Jetzer, and Klein [88].
   - Gravitational wave energy emission and detection rates for PBH hyperbolic encounters were analyzed by García-Bellido and Nesseris [90].

3. **Template Banks:**
   - Template banks for gravitational waveforms from coalescing binary black holes (including non-spinning binaries) were developed by Ajith et al. [92].

4. **Constraints on PBHs:**
   - Constraints on primordial black holes were reviewed by Carr, Kohri, Sendouda, and Yokoyama [98].
   - Universal gravitational wave signatures of cosmological solitons were discussed by Lozanov, Sasaki, and Takhistov [100].

5. **Induced SGWBs:**
   - Doubly peaked induced stochastic gravitational wave backgrounds were tested for baryogenesis from primordial black holes by Bhaumik et al. [101].
   - Distinct signatures of spinning PBH domination and evaporation, including doubly peaked gravitational waves, dark relics, and CMB complementarity, were explored by Bhaumik et al. [101].

6. **Future Detectors:**
   - Future detectors like Taiji, LISA, DECIGO, Big Bang Observer, Cosmic Explorer, Einstein Telescope, and KAGRA are expected to contribute significantly to the detection of SGWBs from PBHs.

7. **Pulsar Timing Arrays:**
   - Pulsar timing arrays have been used to search for an isotropic stochastic gravitational wave background [73-75].

8. **Template Banks and Simulations:**
   - Template banks like those developed by Ajith et al. are crucial for matching observed signals with theoretical predictions.
```

## Non-AI agents

All the agents discussed so far are AI agents, meaning that they are based on LLMs and can be invoked to perform tasks that require natural language understanding and generation. However, the `langchain4j-agentic` module also supports non-AI agents, which can be used to perform tasks that do not require natural language processing, like invoking a REST API or executing a command. These non-AI agents are indeed more similar to tools, but in this context it is convenient to model them as agents, so that they can be used in the same way as AI agents, and mixed with them to compose more powerful and complete agentic systems.

For instance the `ExchangeAgent` used in the supervisor example has been probably inappropriately modelled as an AI agent, and it could be better defined as a non-AI agent that simply invokes a REST API to perform the currency exchange. 

```java
public class ExchangeOperator {

    @Agent(value = "A money exchanger that converts a given amount of money from the original to the target currency",
            outputKey = "exchange")
    public Double exchange(@V("originalCurrency") String originalCurrency, @V("amount") Double amount, @V("targetCurrency") String targetCurrency) {
        // invoke the REST API to perform the currency exchange
    }
}
```

so that it can be used in the same way as the other subagents made available to the supervisor. 

```java
WithdrawAgent withdrawAgent = AgenticServices
        .agentBuilder(WithdrawAgent.class)
        .chatModel(BASE_MODEL)
        .tools(bankTool)
        .build();
CreditAgent creditAgent = AgenticServices
        .agentBuilder(CreditAgent.class)
        .chatModel(BASE_MODEL)
        .tools(bankTool)
        .build();

SupervisorAgent bankSupervisor = AgenticServices
        .supervisorBuilder()
        .chatModel(PLANNER_MODEL)
        .subAgents(withdrawAgent, creditAgent, new ExchangeOperator())
        .build();
```

In essence an agent in `langchain4j-agentic` can be any Java class having one and only one method annotated with the `@Agent` annotation.

Finally, non-AI agents can also be useful to read the state of the `AgenticScope` or execute small operations on it, and for this reason the `AgenticServices` provides an `agentAction` factory method to create a simple agent from a `Consumer<AgenticServices>`. For instance suppose to have a `scorer` agent that produces a `score` as a `String` value, and a subsequent `reviewer` agent that needs to consume that `score` as a `double`. In this case the two agents would be incompatible, but it is possible to adapt the output of the first in the format required by the second using an `agentAction`, rewriting the `score` state of the `AgenticScope` like it follows:

```java
UntypedAgent editor = AgenticServices.sequenceBuilder()
        .subAgents(
                scorer,
                AgenticServices.agentAction(agenticScope -> agenticScope.writeState("score", Double.parseDouble(agenticScope.readState("score", "0.0")))),
                reviewer)
        .build();
```

### Human-in-the-loop

Another common need when building agentic systems is to have a human in the loop, allowing the system to ask user's input for missing information or approval before proceeding with certain actions. This human-in-the-loop capability can be also seen as a special non-AI agent and thus implemented as such.

```java
public record HumanInTheLoop(Consumer<String> requestWriter, Supplier<String> responseReader) {

    @Agent("An agent that asks the user for missing information")
    public String askUser(String request) {
        requestWriter.accept(request);
        return responseReader.get();
    }
}
```

This quite naive, but also very generic, implementation is based on the use of two functions, a `Consumer` of the AI request intended to forward it to the user and a `Supplier`, eventually waiting in a blocking way, of the response provided by the user.

The `HumanInTheLoop` agent provided out-of-the-box by the `langchain4j-agentic` module allows to define these two functions together with the agent description, the state variable of the `AgenticScope` used as input to generate the request for the user and the output variable where the user's response will be written.

For instance, having defined an `AstrologyAgent` like:

```java
public interface AstrologyAgent {
    @SystemMessage("""
        You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
        """)
    @UserMessage("""
        Generate the horoscope for {{name}} who is a {{sign}}.
        """)
    @Agent("An astrologist that generates horoscopes based on the user's name and zodiac sign.")
    String horoscope(@V("name") String name, @V("sign") String sign);
}
```

it is possible to create a `SupervisorAgent` that uses both this AI agent and a `HumanInTheLoop` one to ask the user for their zodiac sign before generating the horoscope, sending its question to the console standard output and reading the user's response from the standard input, as follows:

```java
AstrologyAgent astrologyAgent = AgenticServices
        .agentBuilder(AstrologyAgent.class)
        .chatModel(BASE_MODEL)
        .build();

HumanInTheLoop humanInTheLoop = AgenticServices
        .humanInTheLoopBuilder()
        .description("An agent that asks the zodiac sign of the user")
        .outputKey("sign")
        .requestWriter(request -> {
            System.out.println(request);
            System.out.print("> ");
        })
        .responseReader(() -> System.console().readLine())
        .build();

SupervisorAgent horoscopeAgent = AgenticServices
        .supervisorBuilder()
        .chatModel(PLANNER_MODEL)
        .subAgents(astrologyAgent, humanInTheLoop)
        .build();
```

In this way if the user invokes the `horoscopeAgent` with a request like

```java
horoscopeAgent.invoke("My name is Mario. What is my horoscope?")
```

the supervisor agent will see that the user's zodiac sign is missing, and will invoke the `HumanInTheLoop` agent to ask the user for it, producing the following output:

```
What is your zodiac sign?
> 
```

waiting for the user to provide the answer, which will be then used to invoke the `AstrologyAgent` and generate the horoscope.

Since the user may take some time to provide the answer, it is possible, and actually recommended, to configure the `HumanInTheLoop` agent as an asynchronous one. In this way the agents that don't need the user's input can proceed with their execution while the agentic system is waiting for the user to provide the answer. Note however that the supervisor always enforces blocking execution for all agents in order to allow the planning of the next action to take into account the complete state of the `AgenticScope`. For this reason configuring the `HumanInTheLoop` agent in asynchronous mode wouldn't have any effect in the former example.

## A2A Integration

The additional `langchain4j-agentic-a2a` module provides a seamless integration with the [A2A](https://a2aprotocol.ai/) protocol, allowing to build agentic systems that can use remote A2A server agents and eventually mixing them with other locally defined agents.

For instance if the `CreativeWriter` agent used in the first example was defined on a remote A2A server, it is possible to create an `A2ACreativeWriter` agent that can be used in the same way as a local agent, but invoking the remote one.

```java
UntypedAgent creativeWriter = AgenticServices
        .a2aBuilder(A2A_SERVER_URL)
        .inputKeys("topic")
        .outputKey("story")
        .build();
```

The description of the agent capabilities is automatically retrieved from the agent card provided by the A2A server. This card however doesn't provide a name for the input arguments, so it is necessary to specify them explicitly using the `inputKeys` method.

Alternatively, it is possible to define a local interface for the A2A agent like:

```java
public interface A2ACreativeWriter {

    @Agent
    String generateStory(@V("topic") String topic);
}
```

so that it can be used in a more type-safe way, and the input names are automatically derived from method arguments.

```java
A2ACreativeWriter creativeWriter = AgenticServices
        .a2aBuilder(A2A_SERVER_URL, A2ACreativeWriter.class)
        .outputKey("story")
        .build();
```

This agent can then be used in the same way as a local agent, and mixed with them, when defining a workflow or using it as a subagent for a supervisor.

The remote A2A agent must return a [Task](https://a2a-protocol.org/latest/specification/#61-task-object) type.
