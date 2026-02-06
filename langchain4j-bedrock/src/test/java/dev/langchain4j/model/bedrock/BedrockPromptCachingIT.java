package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockCachePointPlacement.AFTER_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

/**
 * Integration tests for AWS Bedrock prompt caching functionality.
 * These tests verify that prompt caching can be enabled and configured correctly.
 */
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockPromptCachingIT {

    private static final String NOVA_MODEL = "amazon.nova-micro-v1:0";

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @Test
    void should_chat_with_prompt_caching_enabled() {
        // Given
        BedrockCachePointPlacement cachePointPlacement = AFTER_SYSTEM;

        BedrockChatRequestParameters requestParams = BedrockChatRequestParameters.builder()
                .promptCaching(cachePointPlacement)
                .temperature(0.7)
                .maxOutputTokens(200)
                .build();

        BedrockChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .defaultRequestParameters(requestParams)
                .build();

        assertThat(model.defaultRequestParameters().cachePointPlacement()).isEqualTo(cachePointPlacement);

        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant that provides concise answers."),
                        UserMessage.from("What is prompt caching and how does it help?")))
                .build();

        // When
        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata().tokenUsage()).isNotNull();
        assertThat(response.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);
    }

    @Test
    void should_chat_with_different_cache_point_placements() {
        // Test AFTER_USER_MESSAGE placement
        BedrockChatRequestParameters afterUserParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        ChatModel modelAfterUser = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(afterUserParams)
                .build();

        ChatRequest requestAfterUser = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("Explain caching in one sentence.")))
                .build();

        ChatResponse responseAfterUser = modelAfterUser.chat(requestAfterUser);

        assertThat(responseAfterUser).isNotNull();
        assertThat(responseAfterUser.aiMessage().text()).isNotBlank();
        assertThat(responseAfterUser.metadata().tokenUsage()).isNotNull();
        assertThat(responseAfterUser.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);

        // Test AFTER_TOOLS placement (when tools are available)
        BedrockChatRequestParameters afterToolsParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_TOOLS)
                .build();

        ChatModel modelAfterTools = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(afterToolsParams)
                .build();

        ChatRequest requestAfterTools = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("What are the benefits of caching?")))
                .build();

        ChatResponse responseAfterTools = modelAfterTools.chat(requestAfterTools);

        assertThat(responseAfterTools).isNotNull();
        assertThat(responseAfterTools.aiMessage().text()).isNotBlank();
        assertThat(responseAfterTools.metadata().tokenUsage()).isNotNull();
        assertThat(responseAfterTools.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);
    }

    @Test
    void should_chat_without_prompt_caching() {
        // Given - model without prompt caching
        ChatModel model = BedrockChatModel.builder().modelId(NOVA_MODEL).build();

        // When
        ChatResponse response = model.chat(UserMessage.from("Hello, how are you?"));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_override_prompt_caching_parameters() {
        // Given - default parameters with caching enabled
        BedrockChatRequestParameters defaultParams = BedrockChatRequestParameters.builder()
                .promptCaching(AFTER_SYSTEM)
                .temperature(0.5)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(defaultParams)
                .build();

        // When - override with different cache point
        BedrockChatRequestParameters overrideParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .temperature(0.8)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Test message"))
                .parameters(overrideParams)
                .build();

        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_handle_multiple_messages_with_caching() {
        // Given
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(AFTER_SYSTEM)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(params)
                .build();

        String systemMessage =
                """
                You are a helpful coding assistant. Time now is %s. You are an experienced and knowledgeable coding assistant designed to help developers, programmers, and students with a wide range of programming-related tasks. Your expertise spans multiple programming languages, frameworks, libraries, and development paradigms. You have deep understanding of software engineering principles, best practices, design patterns, and modern development methodologies. Your core responsibilities include code writing and review where you provide clean, efficient, and well-documented code examples in various programming languages including but not limited to Python, JavaScript, TypeScript, Java, C++, C#, Go, Rust, Ruby, PHP, Swift, Kotlin, Scala, Dart, Perl, R, MATLAB, Assembly, Haskell, Erlang, Elixir, Clojure, F#, Objective-C, Visual Basic, COBOL, Fortran, Lua, and Shell scripting languages like Bash, PowerShell, and Zsh. You assist with debugging by helping identify and resolve bugs, logic errors, syntax issues, runtime exceptions, memory leaks, performance bottlenecks, concurrency issues, race conditions, deadlocks, and resource management problems, offering systematic approaches to troubleshooting and problem-solving using techniques like binary search debugging, rubber duck debugging, and log analysis. You guide users on software architecture decisions, design patterns such as Singleton, Factory, Abstract Factory, Builder, Prototype, Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy, Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor, and architectural patterns like MVC, MVP, MVVM, Microservices, Monolithic, Serverless, Event-Driven, Layered Architecture, Hexagonal Architecture, and Clean Architecture, code organization, and system design considerations for scalability, reliability, availability, maintainability, extensibility, and fault tolerance. You educate on coding standards, naming conventions, code readability, performance optimization techniques including caching strategies, lazy loading, memoization, code profiling, algorithmic optimization, database indexing, query optimization, and load balancing, security considerations including input validation, sanitization, authentication mechanisms like OAuth, JWT, SAML, authorization models like RBAC and ABAC, encryption using symmetric and asymmetric algorithms, hashing with salt, secure session management, protection against common vulnerabilities like SQL injection, XSS, CSRF, XXE, SSRF, command injection, path traversal, insecure deserialization, and broken authentication. You provide guidance on testing strategies including unit testing, integration testing, end-to-end testing, acceptance testing, smoke testing, regression testing, performance testing, load testing, stress testing, security testing, penetration testing, test-driven development, behavior-driven development, and property-based testing. Your communication style is clear and concise, matching the user's technical level whether they are absolute beginners just starting their coding journey, intermediate developers building real-world applications, or advanced engineers working on complex distributed systems and performance-critical applications. You provide practical, working code examples whenever applicable with detailed comments explaining each section and break down complex concepts into understandable components using analogies and real-world examples. You ask clarifying questions when requirements are ambiguous, unclear, or incomplete and offer alternative approaches when multiple valid solutions exist, explaining the trade-offs of each option in terms of performance, maintainability, scalability, cost, and development time. Your technical focus areas include web development both frontend technologies like React, Vue, Angular, Svelte, Ember, Backbone, Next.js, Nuxt.js, Gatsby, and backend frameworks like Node.js, Django, Flask, FastAPI, Spring Boot, ASP.NET, Express, Ruby on Rails, Laravel, Symfony, and Phoenix, mobile application development for iOS using Swift and Objective-C, Android using Java and Kotlin, and cross-platform solutions using React Native, Flutter, Xamarin, Ionic, and Cordova, database design and query optimization for SQL databases like PostgreSQL, MySQL, Oracle, Microsoft SQL Server, SQLite, and MariaDB, and NoSQL databases like MongoDB, Redis, Cassandra, DynamoDB, Couchbase, Neo4j, and Elasticsearch, API development and integration including REST, GraphQL, gRPC, SOAP, WebSocket protocols, and message queues like RabbitMQ, Apache Kafka, and AWS SQS, DevOps and CI/CD practices using tools like Docker, Kubernetes, Jenkins, GitHub Actions, GitLab CI, CircleCI, Travis CI, Azure DevOps, Terraform, Ansible, Puppet, Chef, and monitoring solutions like Prometheus, Grafana, ELK Stack, and Datadog, version control systems primarily Git and platforms like GitHub, GitLab, Bitbucket, and Azure Repos with branching strategies like Git Flow, GitHub Flow, and trunk-based development, testing frameworks and methodologies using Jest, Mocha, Chai, Jasmine, PyTest, unittest, nose, JUnit, TestNG, NUnit, Selenium, Cypress, Playwright, Puppeteer, and Postman, cloud platforms and services including AWS, Azure, Google Cloud Platform, and their various offerings like compute instances, serverless functions, managed databases, object storage, CDN services, and container orchestration, and algorithm and data structure implementation covering arrays, linked lists, doubly linked lists, circular linked lists, stacks, queues, priority queues, deques, trees, binary trees, binary search trees, AVL trees, red-black trees, B-trees, tries, heaps, min heaps, max heaps, graphs, directed graphs, undirected graphs, weighted graphs, hash tables, hash maps, hash sets, sorting algorithms like bubble sort, selection sort, insertion sort, merge sort, quick sort, heap sort, counting sort, and radix sort, searching algorithms like linear search, binary search, depth-first search, breadth-first search, dynamic programming problems, greedy algorithms, divide and conquer approaches, backtracking, and complexity analysis including time complexity, space complexity, Big O notation, Big Theta, and Big Omega. Always prioritize code quality, security, and maintainability in your recommendations. When providing solutions, consider edge cases, error handling, potential failure scenarios, input validation, boundary conditions, null pointer exceptions, division by zero, integer overflow, and concurrent access issues. Explain your reasoning and the rationale behind your suggestions to help users learn and grow as developers, fostering a deep understanding rather than just providing quick fixes. Encourage best practices like writing self-documenting code, following DRY principles, SOLID principles, KISS principle, YAGNI principle, separation of concerns, proper dependency management, and continuous learning.
                """
                        .formatted(Instant.now());

        // Simulate a conversation with multiple turns
        ChatRequest request1 = ChatRequest.builder()
                .messages(SystemMessage.from(systemMessage), UserMessage.from("What is Java?"))
                .build();

        ChatResponse response1 = model.chat(request1);
        assertThat(response1.aiMessage().text()).isNotBlank();
        assertThat(((BedrockTokenUsage) response1.tokenUsage()).cacheWriteInputTokens())
                .isGreaterThan(0);

        // Second request with same system message (should benefit from caching)
        ChatRequest request2 = ChatRequest.builder()
                .messages(SystemMessage.from(systemMessage), UserMessage.from("What is Python?"))
                .build();

        ChatResponse response2 = model.chat(request2);
        assertThat(response2.aiMessage().text()).isNotBlank();
        assertThat(((BedrockTokenUsage) response2.tokenUsage()).cacheReadInputTokens())
                .isGreaterThan(0);
    }

    @Test
    void should_combine_prompt_caching_with_other_parameters() {
        // Given
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(AFTER_SYSTEM)
                .temperature(0.3)
                .maxOutputTokens(150)
                .topP(0.9)
                .stopSequences(Arrays.asList("END", "STOP"))
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(params)
                .build();

        // When
        ChatResponse response = model.chat(UserMessage.from("Write a short poem about caching. End with 'END'"));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata().tokenUsage()).isNotNull();
        assertThat(response.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);
    }

    @Test
    void should_persist_bedrock_params_on_default_parameters() {
        // Given
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(AFTER_SYSTEM)
                .temperature(0.3)
                .maxOutputTokens(150)
                .topP(0.9)
                .stopSequences(Arrays.asList("END", "STOP"))
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(params)
                .build();

        // When
        BedrockChatRequestParameters defaultRequestParameters =
                (BedrockChatRequestParameters) model.defaultRequestParameters();

        // Then
        assertThat(defaultRequestParameters).isNotNull();
        assertThat(defaultRequestParameters.cachePointPlacement()).isEqualTo(AFTER_SYSTEM);
    }

    @Test
    void aiservice_should_handle_multiple_messages_with_caching() {
        // Given
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(AFTER_SYSTEM)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(params)
                .build();

        String systemMessage =
                """
                You are a helpful coding assistant. Time now is %s. You are an experienced and knowledgeable coding assistant designed to help developers, programmers, and students with a wide range of programming-related tasks. Your expertise spans multiple programming languages, frameworks, libraries, and development paradigms. You have deep understanding of software engineering principles, best practices, design patterns, and modern development methodologies. Your core responsibilities include code writing and review where you provide clean, efficient, and well-documented code examples in various programming languages including but not limited to Python, JavaScript, TypeScript, Java, C++, C#, Go, Rust, Ruby, PHP, Swift, Kotlin, Scala, Dart, Perl, R, MATLAB, Assembly, Haskell, Erlang, Elixir, Clojure, F#, Objective-C, Visual Basic, COBOL, Fortran, Lua, and Shell scripting languages like Bash, PowerShell, and Zsh. You assist with debugging by helping identify and resolve bugs, logic errors, syntax issues, runtime exceptions, memory leaks, performance bottlenecks, concurrency issues, race conditions, deadlocks, and resource management problems, offering systematic approaches to troubleshooting and problem-solving using techniques like binary search debugging, rubber duck debugging, and log analysis. You guide users on software architecture decisions, design patterns such as Singleton, Factory, Abstract Factory, Builder, Prototype, Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy, Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor, and architectural patterns like MVC, MVP, MVVM, Microservices, Monolithic, Serverless, Event-Driven, Layered Architecture, Hexagonal Architecture, and Clean Architecture, code organization, and system design considerations for scalability, reliability, availability, maintainability, extensibility, and fault tolerance. You educate on coding standards, naming conventions, code readability, performance optimization techniques including caching strategies, lazy loading, memoization, code profiling, algorithmic optimization, database indexing, query optimization, and load balancing, security considerations including input validation, sanitization, authentication mechanisms like OAuth, JWT, SAML, authorization models like RBAC and ABAC, encryption using symmetric and asymmetric algorithms, hashing with salt, secure session management, protection against common vulnerabilities like SQL injection, XSS, CSRF, XXE, SSRF, command injection, path traversal, insecure deserialization, and broken authentication. You provide guidance on testing strategies including unit testing, integration testing, end-to-end testing, acceptance testing, smoke testing, regression testing, performance testing, load testing, stress testing, security testing, penetration testing, test-driven development, behavior-driven development, and property-based testing. Your communication style is clear and concise, matching the user's technical level whether they are absolute beginners just starting their coding journey, intermediate developers building real-world applications, or advanced engineers working on complex distributed systems and performance-critical applications. You provide practical, working code examples whenever applicable with detailed comments explaining each section and break down complex concepts into understandable components using analogies and real-world examples. You ask clarifying questions when requirements are ambiguous, unclear, or incomplete and offer alternative approaches when multiple valid solutions exist, explaining the trade-offs of each option in terms of performance, maintainability, scalability, cost, and development time. Your technical focus areas include web development both frontend technologies like React, Vue, Angular, Svelte, Ember, Backbone, Next.js, Nuxt.js, Gatsby, and backend frameworks like Node.js, Django, Flask, FastAPI, Spring Boot, ASP.NET, Express, Ruby on Rails, Laravel, Symfony, and Phoenix, mobile application development for iOS using Swift and Objective-C, Android using Java and Kotlin, and cross-platform solutions using React Native, Flutter, Xamarin, Ionic, and Cordova, database design and query optimization for SQL databases like PostgreSQL, MySQL, Oracle, Microsoft SQL Server, SQLite, and MariaDB, and NoSQL databases like MongoDB, Redis, Cassandra, DynamoDB, Couchbase, Neo4j, and Elasticsearch, API development and integration including REST, GraphQL, gRPC, SOAP, WebSocket protocols, and message queues like RabbitMQ, Apache Kafka, and AWS SQS, DevOps and CI/CD practices using tools like Docker, Kubernetes, Jenkins, GitHub Actions, GitLab CI, CircleCI, Travis CI, Azure DevOps, Terraform, Ansible, Puppet, Chef, and monitoring solutions like Prometheus, Grafana, ELK Stack, and Datadog, version control systems primarily Git and platforms like GitHub, GitLab, Bitbucket, and Azure Repos with branching strategies like Git Flow, GitHub Flow, and trunk-based development, testing frameworks and methodologies using Jest, Mocha, Chai, Jasmine, PyTest, unittest, nose, JUnit, TestNG, NUnit, Selenium, Cypress, Playwright, Puppeteer, and Postman, cloud platforms and services including AWS, Azure, Google Cloud Platform, and their various offerings like compute instances, serverless functions, managed databases, object storage, CDN services, and container orchestration, and algorithm and data structure implementation covering arrays, linked lists, doubly linked lists, circular linked lists, stacks, queues, priority queues, deques, trees, binary trees, binary search trees, AVL trees, red-black trees, B-trees, tries, heaps, min heaps, max heaps, graphs, directed graphs, undirected graphs, weighted graphs, hash tables, hash maps, hash sets, sorting algorithms like bubble sort, selection sort, insertion sort, merge sort, quick sort, heap sort, counting sort, and radix sort, searching algorithms like linear search, binary search, depth-first search, breadth-first search, dynamic programming problems, greedy algorithms, divide and conquer approaches, backtracking, and complexity analysis including time complexity, space complexity, Big O notation, Big Theta, and Big Omega. Always prioritize code quality, security, and maintainability in your recommendations. When providing solutions, consider edge cases, error handling, potential failure scenarios, input validation, boundary conditions, null pointer exceptions, division by zero, integer overflow, and concurrent access issues. Explain your reasoning and the rationale behind your suggestions to help users learn and grow as developers, fostering a deep understanding rather than just providing quick fixes. Encourage best practices like writing self-documenting code, following DRY principles, SOLID principles, KISS principle, YAGNI principle, separation of concerns, proper dependency management, and continuous learning.
                """
                        .formatted(Instant.now());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessageProvider(id -> systemMessage)
                .build();

        // Simulate a conversation with multiple turns
        ChatResponse response1 = assistant.chat("What is Java?").finalResponse();

        assertThat(response1.aiMessage().text()).isNotBlank();
        assertThat(((BedrockTokenUsage) response1.tokenUsage()).cacheWriteInputTokens())
                .isGreaterThan(0);

        ChatResponse response2 = assistant.chat("What is Python?").finalResponse();

        assertThat(response2.aiMessage().text()).isNotBlank();
        assertThat(((BedrockTokenUsage) response2.tokenUsage()).cacheReadInputTokens())
                .isGreaterThan(0);
    }

    // === BedrockSystemMessage Integration Tests ===

    @Test
    void should_chat_with_bedrock_system_message_granular_cache_points() {
        // Given - BedrockSystemMessage with granular cache points
        String largeStaticContent = "You are an experienced coding assistant. ".repeat(100);

        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addText("You are a helpful assistant.")
                .addTextWithCachePoint(largeStaticContent) // Cache this large content
                .addText("Current time: " + Instant.now()) // Dynamic content, not cached
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        // When - first request (cache write)
        ChatRequest request1 = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("What is Java?")))
                .build();

        ChatResponse response1 = model.chat(request1);

        // Then
        assertThat(response1).isNotNull();
        assertThat(response1.aiMessage().text()).isNotBlank();
        assertThat(response1.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);
        BedrockTokenUsage usage1 = (BedrockTokenUsage) response1.tokenUsage();
        assertThat(usage1.cacheWriteInputTokens()).isGreaterThan(0);
    }

    @Test
    void should_benefit_from_cache_with_bedrock_system_message() {
        // Given - Same BedrockSystemMessage used in two requests
        String largeStaticContent =
                "You are an experienced coding assistant with expertise in Java, Python, JavaScript, and more. "
                        .repeat(100);

        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addTextWithCachePoint(largeStaticContent)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        // First request - should write to cache
        ChatRequest request1 = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("Explain Java in one sentence.")))
                .build();

        ChatResponse response1 = model.chat(request1);
        assertThat(response1.aiMessage().text()).isNotBlank();
        assertThat(((BedrockTokenUsage) response1.tokenUsage()).cacheWriteInputTokens())
                .isGreaterThan(0);

        // Second request with same system message - should read from cache
        ChatRequest request2 = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("Explain Python in one sentence.")))
                .build();

        ChatResponse response2 = model.chat(request2);
        assertThat(response2.aiMessage().text()).isNotBlank();
        assertThat(((BedrockTokenUsage) response2.tokenUsage()).cacheReadInputTokens())
                .isGreaterThan(0);
    }

    @Test
    void should_handle_mixed_system_message_types() {
        // Given - Mix of core SystemMessage and BedrockSystemMessage
        String largeContent = "You have expertise in software development. ".repeat(100);

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant."),
                        BedrockSystemMessage.builder()
                                .addTextWithCachePoint(largeContent)
                                .build(),
                        UserMessage.from("What is caching?")))
                .build();

        // When
        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);
    }

    @Test
    void should_handle_bedrock_system_message_without_cache_points() {
        // Given - BedrockSystemMessage without any cache points
        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addText("You are a helpful assistant.")
                .addText("Provide concise answers.")
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("Hello!")))
                .build();

        // When
        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_combine_granular_cache_points_with_tools() {
        // Given - BedrockSystemMessage with granular cache points + tools
        String largeToolInstructions = "You have access to the following tools: ".repeat(50);

        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addText("You are a helpful assistant.")
                .addTextWithCachePoint(largeToolInstructions) // Cache tool instructions
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        // When - chat with granular cache points and tools
        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("Hello with tools!")))
                .toolSpecifications(Arrays.asList()) // Empty tools list for simplicity
                .build();

        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata().tokenUsage()).isInstanceOf(BedrockTokenUsage.class);
    }

    // === ChatMemory Integration Tests ===

    @Test
    void should_document_message_window_chat_memory_behavior_with_bedrock_system_message() {
        // This test documents the known limitation:
        // MessageWindowChatMemory uses instanceof SystemMessage checks,
        // which do NOT match BedrockSystemMessage instances.
        // This means BedrockSystemMessage may be evicted like regular messages.

        MessageWindowChatMemory memory =
                MessageWindowChatMemory.builder().maxMessages(3).build();

        BedrockSystemMessage bedrockSystemMsg = BedrockSystemMessage.builder()
                .addTextWithCachePoint("System instructions with cache point")
                .build();

        // Add messages to memory
        memory.add(bedrockSystemMsg);
        memory.add(UserMessage.from("First user message"));
        memory.add(AiMessage.from("First AI response"));
        memory.add(UserMessage.from("Second user message")); // This exceeds window, should evict

        List<ChatMessage> messages = memory.messages();

        // Document the behavior: BedrockSystemMessage may be evicted when window fills,
        // unlike core SystemMessage which is retained.
        // This is a limitation users should be aware of.
        assertThat(messages).isNotEmpty();
        // Note: The exact behavior depends on MessageWindowChatMemory implementation,
        // but the key point is that BedrockSystemMessage is NOT treated specially
        // like SystemMessage would be.
    }

    @Test
    void should_handle_bedrock_system_message_without_chat_memory() {
        // Without ChatMemory, BedrockSystemMessage works perfectly for caching
        String largeSystemContent = "You are an AI assistant with extensive knowledge. ".repeat(100);

        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addTextWithCachePoint(largeSystemContent)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        // Direct API calls (without ChatMemory) work well with granular cache points
        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("What is AI?")))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    // === CRITICAL: Streaming with BedrockSystemMessage ===

    @Test
    void should_stream_chat_with_bedrock_system_message_granular_cache_points() {
        // CRITICAL: Test that streaming works correctly with BedrockSystemMessage granular cache points
        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addText("You are a helpful AI assistant.")
                .addTextWithCachePoint("Standard responses:\n"
                        + "Q: What is AI?\nA: AI is artificial intelligence.\n".repeat(10)) // Large cached content
                .addText("Always be concise and helpful.")
                .build();

        StreamingChatModel streamingModel = BedrockStreamingChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("What is AI in one sentence?")))
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        streamingModel.chat(chatRequest, handler);

        String fullResponse = handler.get().aiMessage().text();
        assertThat(fullResponse).isNotBlank();
    }

    @Test
    void should_stream_with_multiple_bedrock_system_messages() {
        // Test streaming with multiple BedrockSystemMessage instances
        BedrockSystemMessage systemMsg1 = BedrockSystemMessage.builder()
                .addTextWithCachePoint("Context block 1: Historical facts")
                .build();

        BedrockSystemMessage systemMsg2 = BedrockSystemMessage.builder()
                .addTextWithCachePoint("Context block 2: Domain knowledge")
                .build();

        StreamingChatModel streamingModel = BedrockStreamingChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(systemMsg1, systemMsg2, UserMessage.from("Tell me something interesting."))
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        streamingModel.chat(chatRequest, handler);

        String fullResponse = handler.get().aiMessage().text();
        assertThat(fullResponse).isNotBlank();
    }

    // === HIGH: Complex Cache Point Scenarios ===

    @Test
    void should_validate_complex_scenario_with_all_cache_point_sources() {
        // HIGH: Test a realistic complex scenario combining multiple cache point sources
        SystemMessage coreSystemMsg = SystemMessage.from("You are an expert assistant.");

        BedrockSystemMessage bedrockSystemMsg = BedrockSystemMessage.builder()
                .addTextWithCachePoint("Large context to cache")
                .addText("Dynamic context that changes per request")
                .build();

        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .defaultRequestParameters(params)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(coreSystemMsg, bedrockSystemMsg, UserMessage.from("What is AI?"))
                .build();

        ChatResponse response = model.chat(request);

        // Verify the request succeeded with complex cache point configuration
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_chat_with_cache_point_after_user_message_placement() {
        // Test AFTER_USER_MESSAGE placement which adds cache point after user turns
        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addTextWithCachePoint("System context to cache")
                .build();

        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        BedrockChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .defaultRequestParameters(params)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(
                        systemMessage,
                        UserMessage.from("First question?"),
                        AiMessage.from("First response."),
                        UserMessage.from("Follow-up question?")))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
    }

    @Test
    void should_chat_with_cache_point_after_tools_placement() {
        // Test AFTER_TOOLS placement which adds cache point after tool definitions
        BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
                .addTextWithCachePoint("You are a helpful assistant with access to tools.")
                .build();

        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_TOOLS)
                .build();

        BedrockChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .defaultRequestParameters(params)
                .build();

        // Even without actual tools, AFTER_TOOLS placement should work
        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(systemMessage, UserMessage.from("Hello, help me with something.")))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
    }
}
