# Agentic AI

:::note
This section describes how to build agentic AI applications using the `langchain4j-agentic` module. Please note that the whole module has to be considered experimental and is subject to change in future releases.
:::

## Agentic systems

Although there is no universally agreed definition of an AI agent, several emerging patterns demonstrate how to coordinate and combine the capabilities of multiple AI services to create AI-infused applications that can accomplish more complex tasks. These patterns are often referred to as "agentic systems" or "agentic AI". They typically involve the use of large language models (LLMs) to orchestrate the execution of tasks, manage tool usage, and maintain context across interactions.

According to a [recent article published by Antropic researchers](https://www.anthropic.com/research/building-effective-agents), these Agentic System architectures can be grouped into two main categories: workflows and pure agents.

[![](/docs/static/img/workflow-vs-agents.png)](/tutorials/agentic-ai)

The `langchain4j-agentic` module, discussed in this tutorial, provides a set of abstractions and utilities to help you build workflow and pure agentic AI applications. It allows you to define workflows, manage tool usage, and maintain context across interactions with different LLMs.

## Agents in LangChain4j

A single agent in LangChain4j is a single instance of an LLM intended to perform a specific task or set of tasks. An agent can be defined with an interface with a single method, in a similar way to a normal AI service, just adding the `@Agent` annotation to it.

```java
public interface CreativeWriter {

    @UserMessage("""
            You are a creative writer.
            Generate a draft of a story long no more than 3 sentence around the given topic.
            Return only the story and nothing else.
            The topic is {{topic}}.
            """)
    @Agent("Generate a story based on the given topic")
    String generateStory(@V("topic") String topic);
}
```

It is a good practice to also provide with that annotation a short description of the agent's purpose, especially if it is intended to be used in pure agentic patterns, where other agents need to know the capabilities of this agent to make an informed decision on how and when using it.

It is not possible to build an instance of this agent using the `AgentServices.agentBuilder()` method, specifying the interface and the chat model to use. 

```java
CreativeWriter creativeWriter = AgentServices.agentBuilder(CreativeWriter.class)
        .chatModel(myChatModel)
        .outputName("story")
        .build();
```

The other main difference with a plain AI service is the presence of the `outputName` parameter that is used to specify the name of the shared variable where the result of the agent invocation will be stored in order to make it available for other agents in the same agentic system.

## Enter the Cognisphere

The langchain4j-agentic module introduces the concept of a `Cognisphere`, which is a collection of data shared between agents in an agentic system. The `Cognisphere` is used to store shared variables, which can be accessed and modified by different agents within the same system. This allows agents to communicate and collaborate effectively, sharing information and results as needed.

The `Cognisphere` also automatically registers other relevant information like the sequence of invocations of all agents with their responses. It is automatically created when the main agent of the agentic system is invoked and programmatically provided through callbacks when necessary. The different possible usages of the `Cognisphere` will be clarified with practical examples when discussing the agentic patterns implemented by `langchain4j-agentic`.

## Workflow patterns

The `langchain4j-agentic` module provides a set of abstractions to programmatically orchestrate multiple agents and create agentic workflow patterns. These patterns can be combined to create more complex workflows. 

### Sequential workflow

A sequential workflow is the simplest possible pattern where multiple agents are invoked one after the other, with each agent's output being passed as input to the next agent. This pattern is useful when you have a series of tasks that need to be performed in a specific order.

For example, it would be a good idea to complement the `CreativeWriter` agent defined before, with an `AudienceEditor` agent that can edit the generated story to better fit a specific audience

```java
public interface AudienceEditor {

    @UserMessage("""
        You are a professional editor.
        Analyze and rewrite the following story to better align with the target audience of {{audience}}.
        Return only the story and nothing else.
        The story is "{{story}}".
        """)
    @Agent("Edit a story to better fit a given audience")
    String editStory(@V("story") String story, @V("audience") String audience);
}
```

and with a very similar `StyleEditor` doing the same job but for a specific style.

Note that the input arguments of this agent are annotated with a variable name. In fact the values of the arguments to be passed to the agent are not provided directly, but rather taken from the `Cognisphere` shared variables having those names. This allows the agent to access the output of previous agents in the workflow.

At this point it is possible to create a sequential workflow that combines these three agents, where the output of the `CreativeWriter` is passed as input to both the `AudienceEditor` and `StyleEditor`, and the final output is the edited story.

```java
CreativeWriter creativeWriter = AgentServices.agentBuilder(CreativeWriter.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

AudienceEditor audienceEditor = AgentServices.agentBuilder(AudienceEditor.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

StyleEditor styleEditor = AgentServices.agentBuilder(StyleEditor.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

UntypedAgent novelCreator = AgentServices.sequenceBuilder()
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

The values in that input map are copied into the `Cognisphere` shared variables, so that they can be accessed by the subagents. The output of the `novelCreator` agent is also taken from the `Cognisphere` shared variable named "story", which has been formerly rewritten by other all other agents during the novel creation and editing workflow execution.

Optionally, the workflow agent can also be provided with typed interface, so that it can be invoked with a strongly typed input and output. In this case, the `UntypedAgent` interface can be replaced with a more specific one, like:

```java
public interface NovelCreator {

    @Agent
    String createNovel(@V("topic") String topic, @V("audience") String audience, @V("style") String style);
}
```

so that the `novelCreator` agent can be created and used as follows:

```java
NovelCreator novelCreator = AgentServices.sequenceBuilder(NovelCreator.class)
        .subAgents(creativeWriter, audienceEditor, styleEditor)
        .outputName("story")
        .build();

String story = novelCreator.createNovel("dragons and wizards", "young adults", "fantasy");
```

### Loop workflow

A common way to better leverage the capabilities of LLMs is to use them to iteratively refine a piece of text, like a story, by repeatedly invoking an agent that can edit or improve it. This can be achieved by using a loop workflow pattern, where an agent is invoked multiple times until a certain condition is met.

To this purpose it can be used a `StyleScorer` agent that gives a score to a story based on how well it aligns with the required style. 

```java
public interface StyleScorer {

    @UserMessage("""
            You are a critical reviewer.
            Give a review score between 0.0 and 1.0 for the following story based on how well it aligns with the style '{{style}}'.
            Return only the score and nothing else.
            
            The story is: "{{story}}"
            """)
    @Agent("Score a story based on how well it aligns with a given style")
    double scoreStyle(@V("story") String story, @V("style") String style);
}
```

Then it is possible to use this agent in a loop with the `StyleEditor` one to iteratively improve the story until the score reaches a certain threshold, like 0.8, or until a maximum number of iterations is reached.

```java
StyleEditor styleEditor = AgentServices.agentBuilder(StyleEditor.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

StyleScorer styleScorer = AgentServices.agentBuilder(StyleScorer.class)
        .chatModel(BASE_MODEL)
        .outputName("score")
        .build();

UntypedAgent styleReviewLoop = AgentServices.loopBuilder()
        .subAgents(styleScorer, styleEditor)
        .maxIterations(5)
        .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
        .build();
```

Here the `styleEditor` agent writes its output to the `Cognisphere` shared variable named "score", and the same variable is accessed and evaluated in the exit condition of the loop.

At this point the `styleReviewLoop` agent can be seen as a single agent and put in a sequence with the `CreativeWriter` agent to create a `StyledWriter` agent

```java
public interface StyledWriter {

    @Agent
    String writeStoryWithStyle(@V("topic") String topic, @V("style") String style);
}
```

implementing a more complex workflow that combines the story generation and style review process.

```java
CreativeWriter creativeWriter = AgentServices.agentBuilder(CreativeWriter.class)
        .chatModel(BASE_MODEL)
        .outputName("story")
        .build();

StyledWriter styledWriter = AgentServices.sequenceBuilder(StyledWriter.class)
        .subAgents(creativeWriter, styleReviewLoop)
        .outputName("story")
        .build();

String story = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
```

### Parallel workflow

TBD

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
    @Agent("Categorize a user request")
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
CategoryRouter routerAgent = AgentServices.agentBuilder(CategoryRouter.class)
        .chatModel(BASE_MODEL)
        .outputName("category")
        .build();

MedicalExpert medicalExpert = AgentServices.agentBuilder(MedicalExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("response")
        .build();
LegalExpert legalExpert = AgentServices.agentBuilder(LegalExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("response")
        .build();
TechnicalExpert technicalExpert = AgentServices.agentBuilder(TechnicalExpert.class)
        .chatModel(BASE_MODEL)
        .outputName("response")
        .build();

UntypedAgent expertsAgent = AgentServices.conditionalBuilder()
        .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
        .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
        .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
        .build();

ExpertRouterAgent expertRouterAgent = AgentServices.sequenceBuilder(ExpertRouterAgent.class)
        .subAgents(routerAgent, expertsAgent)
        .outputName("response")
        .build();

String response = expertRouterAgent.ask("I broke my leg what should I do");
```

## Memory and context engineering

All agents discussed so far are stateless, meaning that they do not maintain any context or memory of previous interactions. However, like for any other AI service, it is possible to provide agents with a `ChatMemory`, allowing them to maintain context across multiple invocations. 

To provide the former `MedicalExpert` with a memory, it is sufficient to add a field annotated with `@MemoryID` to its signature.

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
MedicalExpertWithMemory medicalExpert = AgentServices.agentBuilder(MedicalExpertWithMemory.class)
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

To solve this problem it is necessary to provide the legal expert with the context and what happened before its invocation, and this is another use case where the information automatically stored in the `Cognisphere` can come to help.

In particular the `Cognisphere` keeps track of the sequence of invocations of all agents, and can produce a context concatenating those invocations in a single conversation. This context can be used as it is or if necessary summarized to a shorter version, for instance defining a `ContextSummarizer` agent.

```java
public interface ContextSummarizer {

    @UserMessage("""
        Create a very short summary, 2 sentences at most, of the following conversation between an AI agent and a user.

        The user conversation is: '{{it}}'.
        """)
    String summarize(String conversation);
}
```

Using this agent, the legal expert can be redefined and provided with a context summarization of the previous conversation, so that it can take into account the previous interactions when answering the new question.

```java
LegalExpertWithMemory legalExpert = spy(AgentServices.agentBuilder(LegalExpertWithMemory.class)
        .chatModel(BASE_MODEL)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
        .context(cognisphere -> contextSummarizer.summarize(cognisphere.contextAsConversation()))
        .outputName("response")
        .build());
```

More in general the context provided to an agent can be any function of the `Cognisphere` state. With this setup, the legal expert, when asked if the neighbor should be sued for the damage he caused, will be able to take into account the previous conversation with the medical expert and provide a more informed answer.

Internally the agentic framework provides the additional context to the legal expert by automatically rewriting the user message sent to it, so that it contains the summarized context of the previous conversation, so in this case the actual user message will be something like:

```
"Considering this context \"The user asked about what to do after breaking their leg, and the AI provided medical advice on immediate actions like immobilizing the leg, applying ice, and seeking medical attention.\"\nYou are a legal expert.\nAnalyze the following user request under a legal point of view and provide the best possible answer.\nThe user request is Should I sue my neighbor who caused this damage?.\n"
```

## Pure agentic AI

Up to this point all agents have been wired and combined to create agentic systems using deterministic workflows. However, there are cases where the agentic system needs to be more flexible and adaptive, allowing agents to make decisions on how to proceed based on the context and the results of previous interactions. This is often referred to as "pure agentic AI".

To this purpose, the `langchain4j-agentic` module provides out-of-the-box a supervisor agent that can be provided with a set of subagents and can autonomously generate a plan, deciding which one to invoke agent to invoke next or if the assigned task has been completed.

To provide an example of how this works let's define a few agents that can be credit or withdraw money from a bank account or exchange a given amount from one currency to another.

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

It is now possible to create instances of these agents as usual using the `AgentServices.agentBuilder()` method, and then use them as subagents of the supervisor agent.

```java
SupervisorAgent bankSupervisor = SupervisorAgentService.builder(PLANNER_MODEL)
        .subAgents(withdrawAgent, creditAgent, exchangeAgent)
        .build();
```

Note that the subagents can also be complex agents implementing a workflow, that will be seen as a single agent by the supervisor.

The resulting `SupervisorAgent` typically takes in input a user request and produces a response so its signature is simply as follows:

```java
public interface SupervisorAgent {
    @Agent
    String process(@V("request") String request);
}
```

Now let's suppose to invoke this agent with the following request:

```java
bankSupervisor.process("Transfer 100 EUR from Mario's account to Georgios' one")
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

In many cases, like this one, this summary is the final response that should be returned to the user, but not always. Suppose that you use the `SupervisorAgent` instead of a plain sequence workflow to create a story and edit it according to a given style and audience as in the very first example. In this case the user will be interested only in the final story, and not in a resume of the intermediate steps taken to create it.

For this reason both the summary generated by the supervisor and the last response of the last invoked agent are passed, together with the original user request, to another agent that gives a score to the relevance of the 2 possible responses for the given request, and returns the one with the highest score. For instance, for the banking example, it could produce the following response scores:

```
ResponseScore{finalResponse=0.3, summary=1.0}
```

thus making the supervisor agent to return the summary as the final response to the user request. The architecture of the supervisor agent is shown in the following diagram:

[![](/docs/static/img/supervisor.png)](/tutorials/agentic-ai)

## A2A Integration

The `langchain4j-agentic` module provides a seamless integration with the [A2A](https://a2aprotocol.ai/) protocol, allowing to build agentic systems that can use agents running on remote A2A servers and eventually mixing them with other locally defined agents.

For instance if the `CreativeWriter` agent used in the first example was defined on a remote A2A server, it is possible to create an `A2ACreativeWriter` agent that can be used in the same way as a local agent, but invoking the remote one.

```java
UntypedAgent creativeWriter = AgentServices.a2aBuilder(A2A_SERVER_URL)
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
A2ACreativeWriter creativeWriter = AgentServices.a2aBuilder(A2A_SERVER_URL, A2ACreativeWriter.class)
    .outputName("story")
    .build();
```

This agent can then be used in the same way as a local agent, and mixed with them, when defining a workflow or using it as a subagent for a supervisor.
