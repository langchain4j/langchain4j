---
sidebar_position: 7
---

# Agents and Agentic AI

:::note
This section describes how to build agentic AI applications using the `langchain4j-agentic` module. Please note that the whole module has to be considered experimental and is subject to change in future releases.
:::

## Agentic systems

Although there is no universally agreed definition of an AI agent, several emerging patterns demonstrate how to coordinate and combine the capabilities of multiple AI services to create AI-infused applications that can accomplish more complex tasks. These patterns are often referred to as "agentic systems" or "agentic AI". They typically involve the use of large language models (LLMs) to orchestrate the execution of tasks, manage tool usage, and maintain context across interactions.

According to a [recent article published by Antropic researchers](https://www.anthropic.com/research/building-effective-agents), these Agentic System architectures can be grouped into two main categories: workflows and pure agents.

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
        .outputName("story")
        .build();
```

In essence agents are plain AI services, providing the same features, but with the ability to be combined with other agents to create more complex workflows and agentic systems. 

The other main difference with an AI service is the presence of the `outputName` parameter that is used to specify the name of the shared variable where the result of the agent invocation will be stored in order to make it available for other agents in the same agentic system. Alternatively, the output name can be also declared directly in the `@Agent` annotation instead of programmatically like in this example, so that it could be omitted in the code and added here.

```java
@Agent(outputName = "story", description = "Generates a story based on the given topic")
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
        .outputName("story")
        .build();

AudienceEditor audienceEditor = AgenticServices
        .agentBuilder(AudienceEditor.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

StyleEditor styleEditor = AgenticServices
        .agentBuilder(StyleEditor.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

UntypedAgent novelCreator = AgenticServices
        .sequenceBuilder()
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .outputName("story")
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
        .outputName("story")
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
        .outputName("story")
        .build();

StyleScorer styleScorer = AgenticServices
        .agentBuilder(StyleScorer.class)
        .chatModel(BASE_MODEL)
        .outputName("score")
        .build();

UntypedAgent styleReviewLoop = AgenticServices
        .loopBuilder()
        .subAgents(styleScorer, styleEditor)
        .maxIterations(5)
        .exitCondition( agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
        .build();
```

Here the `styleScorer` agent writes its output to the `AgenticScope` shared variable named "score", and the same variable is accessed and evaluated in the exit condition of the loop.

At this point the `styleReviewLoop` agent can be seen as a single agent and put in a sequence with the `CreativeWriter` agent to create a `StyledWriter` agent

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
        .outputName("story")
        .build();

StyledWriter styledWriter = AgenticServices
        .sequenceBuilder(StyledWriter.class)
        .subAgents(creativeWriter, styleReviewLoop)
        .outputName("story")
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
        .outputName("meals")
        .build();

MovieExpert movieExpert = AgenticServices
        .agentBuilder(MovieExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("movies")
        .build();

EveningPlannerAgent eveningPlannerAgent = AgenticServices
        .parallelBuilder(EveningPlannerAgent.class)
        .subAgents(foodExpert, movieExpert)
        .executor(Executors.newFixedThreadPool(2))
        .outputName("plans")
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
        .outputName("category")
        .build();

MedicalExpert medicalExpert = AgenticServices
        .agentBuilder(MedicalExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("response")
        .build();
LegalExpert legalExpert = AgenticServices
        .agentBuilder(LegalExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("response")
        .build();
TechnicalExpert technicalExpert = AgenticServices
        .agentBuilder(TechnicalExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("response")
        .build();

UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
        .subAgents( agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
        .subAgents( agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
        .subAgents( agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
        .build();

ExpertRouterAgent expertRouterAgent = AgenticServices
        .sequenceBuilder(ExpertRouterAgent.class)
        .subAgents(routerAgent, expertsAgent)
        .outputName("response")
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
        .outputName("meals")
        .build();

MovieExpert movieExpert = AgenticServices
        .agentBuilder(MovieExpert.class)
        .chatModel(BASE_MODEL)
        .async(true)
        .outputName("movies")
        .build();

EveningPlannerAgent eveningPlannerAgent = AgenticServices
        .sequenceBuilder(EveningPlannerAgent.class)
        .subAgents(foodExpert, movieExpert)
        .executor(Executors.newFixedThreadPool(2))
        .outputName("plans")
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
        .outputName("story")
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
        .outputName("story")
        .build();
```

## Declarative API

All the workflow patterns discussed so far can be defined using a declarative API, which allows you to define workflows in a more concise and readable way. The `langchain4j-agentic` module provides a set of annotations that can be used to define agents and their workflows in a more declarative style.

For instance the `EveningPlannerAgent` implementing the parallel workflow programmatically defined in the previous section can be rewritten using the declarative API as follows:

```java
public interface EveningPlannerAgent {

    @ParallelAgent(outputName = "plans", subAgents = {
            @SubAgent(type = FoodExpert.class, outputName = "meals"),
            @SubAgent(type = MovieExpert.class, outputName = "movies")
    })
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
    @Agent
    List<String> findMeal(@V("mood") String mood);

    @ChatModelSupplier
    static ChatModel chatModel() {
        return FOOD_MODEL;
    }
}
```

In a very similar way, annotating other `static` methods in the agent interface, it is possible to declaratively configure other aspects of the agent like its chat memory, the tools it can use, and so on. Those methods must have no arguments except for the one annotated with `@ChatMemoryProviderSupplier`. The list of annotations available to this purpose follows:

| Annotation Name               | Description                                                                                                                                               |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@ChatModelSupplier`          | Returns the `ChatModel` to be used by this agent.                                                                                                         |
| `@ChatMemorySupplier`         | Returns the `ChatMemory` to be used by this agent.                                                                                                        |
| `@ChatMemoryProviderSupplier` | Returns the `ChatMemoryProvider` to be used by this agent.<br/>This method requires as argument an `Object` to be used as the memoryId of the created memory. |
| `@ContentRetrieverSupplier` | Returns the `ContentRetriever` to be used by this agent.                                                                                                  |
| `@RetrievalAugmentorSupplier` | Returns the `RetrievalAugmentor` to be used by this agent.                                                                                                |
| `@ToolsSupplier` | Returns the tool or set of tools to be used by this agent.<br/> It can return either a single `Object` or a `Object[]`                                         |
| `@ToolProviderSupplier` | Returns the `ToolProvider` to be used by this agent.                                        |

To give another example of this declarative API, let's redefine through it the `ExpertsAgent` demonstrated in the conditional workflow section.

```java
public interface ExpertsAgent {

    @ConditionalAgent(outputName = "response", subAgents = {
            @SubAgent(type = MedicalExpert.class, outputName = "response"),
            @SubAgent(type = TechnicalExpert.class, outputName = "response"),
            @SubAgent(type = LegalExpert.class, outputName = "response")
    })
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
        .outputName("response")
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
        .outputName("response")
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
        .outputName("response")
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

ExchangeAgent exchange = AgenticServices
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

## Non-AI agents

All the agents discussed so far are AI agents, meaning that they are based on LLMs and can be invoked to perform tasks that require natural language understanding and generation. However, the `langchain4j-agentic` module also supports non-AI agents, which can be used to perform tasks that do not require natural language processing, like invoking a REST API or executing a command. These non-AI agents are indeed more similar to tools, but in this context it is convenient to model them as agents, so that they can be used in the same way as AI agents, and mixed with them to compose more powerful and complete agentic systems.

For instance the `ExchangeAgent` used in the supervisor example has been probably inappropriately modelled as an AI agent, and it could be better defined as a non-AI agent that simply invokes a REST API to perform the currency exchange. 

```java
public class ExchangeOperator {

    @Agent(value = "A money exchanger that converts a given amount of money from the original to the target currency",
            outputName = "exchange")
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
        .outputName("sign")
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
        .inputNames("topic")
        .outputName("story")
        .build();
```

The description of the agent capabilities is automatically retrieved from the agent card provided by the A2A server. This card however doesn't provide a name for the input arguments, so it is necessary to specify them explicitly using the `inputNames` method.

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
        .outputName("story")
        .build();
```

This agent can then be used in the same way as a local agent, and mixed with them, when defining a workflow or using it as a subagent for a supervisor.

The remote A2A agent must return a [Task](https://a2a-protocol.org/latest/specification/#61-task-object) type.
