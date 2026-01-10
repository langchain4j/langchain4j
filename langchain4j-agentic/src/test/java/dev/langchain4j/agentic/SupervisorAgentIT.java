package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.LoanApplicationEvaluator;
import dev.langchain4j.agentic.Agents.LoanApplicationExtractor;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.RouterAgent;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.Agents.ColorExpert;
import dev.langchain4j.agentic.Agents.ColorMixerExpert;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class SupervisorAgentIT {

    @Test
    void tools_as_agents_tests() {
        // Nothing conceptually new, just test that now we can use AiServices as tools

        MedicalExpert medicalExpert = spy(
                AiServices.builder(MedicalExpert.class).chatModel(baseModel()).build());
        LegalExpert legalExpert =
                spy(AiServices.builder(LegalExpert.class).chatModel(baseModel()).build());
        TechnicalExpert technicalExpert = spy(
                AiServices.builder(TechnicalExpert.class).chatModel(baseModel()).build());

        RouterAgent routerAgent = AiServices.builder(RouterAgent.class)
                .chatModel(baseModel())
                .tools(medicalExpert, legalExpert, technicalExpert)
                .build();

        System.out.println(routerAgent.askToExpert("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    public interface RequestClassifierAgent {

        @UserMessage(
                """
            Categorize the user request returning only one word among 'legal', 'medical' or 'technical',
            and nothing else, avoiding any explanation.

            The user request is: '{{request}}'.
            """)
        @Agent("An agent that categorizes the user request")
        String categorizeRequest(@V("request") String request);
    }

    @Test
    void agents_system_test() {
        // All agents are registered in the AgentsSystem, which internally uses a planner agent that can invoke other
        // agents

        RequestClassifierAgent requestClassifierAgent = AgenticServices.agentBuilder(RequestClassifierAgent.class)
                .chatModel(baseModel())
                .build();
        MedicalExpert medicalExpert = spy(AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .build());
        LegalExpert legalExpert = spy(AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .build());
        TechnicalExpert technicalExpert = spy(AgenticServices.agentBuilder(TechnicalExpert.class)
                .chatModel(baseModel())
                .build());

        SupervisorAgent askToExpert = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .subAgents(requestClassifierAgent, medicalExpert, legalExpert, technicalExpert)
                .build();

        System.out.println(askToExpert.invoke("I broke my leg what should I do"));

        verify(medicalExpert).medical(any());
    }

    @Test
    void supervisor_in_sequence_test() {
        RequestClassifierAgent requestClassifierAgent = AgenticServices.agentBuilder(RequestClassifierAgent.class)
                .chatModel(baseModel())
                .build();
        MedicalExpert medicalExpert = spy(AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .build());
        LegalExpert legalExpert = spy(AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .build());
        TechnicalExpert technicalExpert = spy(AgenticServices.agentBuilder(TechnicalExpert.class)
                .chatModel(baseModel())
                .build());

        SupervisorAgent askToExpert = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .subAgents(requestClassifierAgent, medicalExpert, legalExpert, technicalExpert)
                .build();

        UntypedAgent askToExpertSequence = AgenticServices.sequenceBuilder()
                .subAgents(askToExpert)
                .outputKey("response")
                .build();

        System.out.println(askToExpertSequence.invoke(Map.of("request", "I broke my leg what should I do")));

        verify(medicalExpert).medical(any());
    }

    public interface BankerAgent {

        @UserMessage(
                """
            You are a banker that executes user request crediting or withdrawing US dollars (USD) from an account,
            using the tools provided and returning the final balance.

            The user request is: '{{it}}'.
            """)
        String execute(@P("request") String request);
    }

    public interface WithdrawAgent {
        @SystemMessage(
                """
            You are a banker that can only withdraw US dollars (USD) from a user account.
            """)
        @UserMessage(
                """
            Withdraw {{amountInUSD}} USD from {{withdrawUser}}'s account and return the new balance.
            """)
        @Agent("A banker that withdraw USD from an account")
        String withdraw(@V("withdrawUser") String withdrawUser, @V("amountInUSD") Double amountInUSD);
    }

    public interface CreditAgent {
        @SystemMessage(
                """
            You are a banker that can only credit US dollars (USD) to a user account.
            """)
        @UserMessage(
                """
            Credit {{amountInUSD}} USD to {{creditUser}}'s account and return the new balance.
            """)
        @Agent("A banker that credit USD to an account")
        String credit(@V("creditUser") String creditUser, @V("amountInUSD") Double amountInUSD);
    }

    static class BankTool {

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

    @Test
    void banker_test() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        BankerAgent bankerAgent = AiServices.builder(BankerAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        System.out.println(bankerAgent.execute("Withdraw 100 USD from Mario's account"));
        assertThat(bankTool.getBalance("Mario")).isEqualTo(900.0);
    }

    @Test
    void agentic_banker_test() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        WithdrawAgent withdrawAgent = AgenticServices.agentBuilder(WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();
        CreditAgent creditAgent = AgenticServices.agentBuilder(CreditAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        SupervisorAgent bankSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY) // default
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(withdrawAgent, creditAgent)
                .build();

        String result = bankSupervisor.invoke("Transfer 100 USD from Mario's account to Georgios' one");
        System.out.println(result);

        assertThat(bankTool.getBalance("Mario")).isEqualTo(900.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1100.0);
    }

    public interface ExchangeAgent {
        @UserMessage(
                """
            You are an operator exchanging money in different currencies.
            Use the tool to exchange {{amount}} {{originalCurrency}} into {{targetCurrency}}
            returning only the final amount provided by the tool as it is and nothing else.
            """)
        @Agent(outputKey = "exchange")
        Double exchange(
                @V("originalCurrency") String originalCurrency,
                @V("amount") Double amount,
                @V("targetCurrency") String targetCurrency);
    }

    static class ExchangeTool {

        public static Map<String, Double> exchangeRatesToUSD = new HashMap<>();

        static {
            exchangeRatesToUSD.put("USD", 1.0);
            exchangeRatesToUSD.put("EUR", 1.15);
            exchangeRatesToUSD.put("CHF", 1.25);
            exchangeRatesToUSD.put("CAN", 0.8);
        }

        @Tool("Exchange the given amount of money from the original to the target currency")
        Double exchange(
                @P("originalCurrency") String originalCurrency,
                @P("amount") Double amount,
                @P("targetCurrency") String targetCurrency) {
            Double exchangeRate1 = exchangeRatesToUSD.get(originalCurrency);
            if (exchangeRate1 == null) {
                throw new RuntimeException("No exchange rate found for currency " + originalCurrency);
            }
            Double exchangeRate2 = exchangeRatesToUSD.get(targetCurrency);
            if (exchangeRate2 == null) {
                throw new RuntimeException("No exchange rate found for currency " + targetCurrency);
            }
            return (amount * exchangeRate1) / exchangeRate2;
        }
    }

    public static class ExchangeOperator {
        public static Map<String, Double> exchangeRatesToUSD = new HashMap<>();

        static {
            exchangeRatesToUSD.put("USD", 1.0);
            exchangeRatesToUSD.put("EUR", 1.15);
            exchangeRatesToUSD.put("CHF", 1.25);
            exchangeRatesToUSD.put("CAN", 0.8);
        }

        @Agent(
                description =
                        "A money exchanger that converts a given amount of money from the original to the target currency",
                outputKey = "exchange")
        public Double exchange(
                @V("originalCurrency") String originalCurrency,
                @V("amount") Double amount,
                @V("targetCurrency") String targetCurrency) {
            Double exchangeRate1 = exchangeRatesToUSD.get(originalCurrency);
            if (exchangeRate1 == null) {
                throw new RuntimeException("No exchange rate found for currency " + originalCurrency);
            }
            Double exchangeRate2 = exchangeRatesToUSD.get(targetCurrency);
            if (exchangeRate2 == null) {
                throw new RuntimeException("No exchange rate found for currency " + targetCurrency);
            }
            return (amount * exchangeRate1) / exchangeRate2;
        }
    }

    @Test
    void agentic_banker_with_agentic_exchange_test() {
        agentic_banker_with_exchange_test(true, false);
    }

    @Test
    void agentic_banker_with_conflicting_names_test() {
        agentic_banker_with_exchange_test(true, true);
    }

    @Test
    void agentic_banker_with_non_agentic_exchange_test() {
        agentic_banker_with_exchange_test(false, false);
    }

    @Test
    @Disabled("Flaky test, needs investigation and rework to make it more reliable")
    void agentic_banker_with_non_agentic_exchange_and_conflicting_names_test() {
        agentic_banker_with_exchange_test(false, true);
    }

    @Test
    void agentic_banker_with_italian_request_test() {
        agentic_banker_with_exchange_test(false, false, "Trasferisci 100 EUR dal conto di Mario a quello di Georgios");
    }

    private void agentic_banker_with_exchange_test(boolean fullyAI, boolean conflictingNames) {
        agentic_banker_with_exchange_test(
                fullyAI, conflictingNames, "Transfer 100 EUR from Mario's account to Georgios' one");
    }

    private void agentic_banker_with_exchange_test(boolean fullyAI, boolean conflictingNames, String userRequest) {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        var withdrawAgentBuilder = AgenticServices.agentBuilder(WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool);
        if (conflictingNames) {
            withdrawAgentBuilder.name("banker");
        }
        WithdrawAgent withdrawAgent = withdrawAgentBuilder.build();

        var creditAgentBuilder = AgenticServices.agentBuilder(CreditAgent.class)
                .chatModel(baseModel())
                .tools(bankTool);
        if (conflictingNames) {
            creditAgentBuilder.name("banker");
        }
        CreditAgent creditAgent = creditAgentBuilder.build();

        Object exchangeAgent;

        if (fullyAI) {
            // Using an AI agent
            exchangeAgent = AgenticServices.agentBuilder(ExchangeAgent.class)
                    .chatModel(baseModel())
                    .description(
                            "A money exchanger that converts a given amount of money from the original to the target currency")
                    .tools(new ExchangeTool())
                    .build();
        } else {
            // Using a non-AI agent
            exchangeAgent = new ExchangeOperator();
        }

        List<String> toolCalls = new ArrayList<>();
        Map<String, Double> toolResults = new HashMap<>();

        SupervisorAgent bankSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY)
                .subAgents(withdrawAgent, creditAgent, exchangeAgent)
                .listener(new AgentListener() {
                    @Override
                    public void afterToolExecution(ToolExecution toolExecution) {
                        toolResults.put(toolExecution.request().name(), (Double) toolExecution.resultObject());
                    }

                    @Override
                    public void beforeToolExecution(BeforeToolExecution beforeToolExecution) {
                        toolCalls.add(beforeToolExecution.request().name());
                    }

                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }
                })
                .build();

        ResultWithAgenticScope<String> result = bankSupervisor.invokeWithAgenticScope(userRequest);
        System.out.println(result.result());

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);

        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.1));

        assertThat(toolCalls).hasSize(fullyAI ? 3 : 2);
        assertThat(toolResults).hasSize(fullyAI ? 3 : 2);
        assertThat(toolCalls).contains("credit", "withdraw");

        assertThat(toolResults.get("credit")).isCloseTo(1115.0, offset(0.1));
        assertThat(toolResults.get("withdraw")).isCloseTo(885.0, offset(0.1));

        if (fullyAI) {
            assertThat(toolCalls).contains("exchange");
            assertThat(toolResults.get("exchange")).isCloseTo(115.0, offset(0.1));
        }
    }

    public record TransactionDetails(String fromUser, String toUser, Double amountInUSD) {}

    public interface TypedBankerAgent {

        @Agent
        TransactionDetails execute(@V("request") String request);
    }

    @Test
    void typed_banker_test_with_maxAgentsInvocations() {
        typed_banker_test(true);
    }

    @Test
    void typed_banker_test_without_maxAgentsInvocations() {
        typed_banker_test(false);
    }

    private void typed_banker_test(boolean useMaxAgentsInvocations) {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("exchange")
                .description("Exchange the given amount of money from the original to the target currency")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("originalCurrency")
                        .addNumberProperty("amount")
                        .addStringProperty("targetCurrency")
                        .build())
                .build();

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
            String originalCurrency = (String) arguments.get("originalCurrency");
            assertThat(originalCurrency).isEqualTo("EUR");
            Double amount = ((Number) arguments.get("amount")).doubleValue();
            String targetCurrency = (String) arguments.get("targetCurrency");
            assertThat(targetCurrency).isEqualTo("USD");
            return "" + new ExchangeTool().exchange(originalCurrency, amount, targetCurrency);
        };

        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        WithdrawAgent withdrawAgent = AgenticServices.agentBuilder(WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();
        CreditAgent creditAgent = AgenticServices.agentBuilder(CreditAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        ExchangeAgent exchangeAgent = AgenticServices.agentBuilder(ExchangeAgent.class)
                .chatModel(baseModel())
                .description(
                        "A money exchanger that converts a given amount of money from the original to the target currency")
                .tools(singletonMap(toolSpecification, toolExecutor))
                .build();

        var supervisorBuilder = AgenticServices.supervisorBuilder(TypedBankerAgent.class)
                .chatModel(plannerModel())
                .output(agenticScope -> new TransactionDetails(
                        agenticScope.readState("withdrawUser", ""),
                        agenticScope.readState("creditUser", ""),
                        agenticScope.readState("amountInUSD", 0.0)))
                .subAgents(withdrawAgent, creditAgent, exchangeAgent);

        if (useMaxAgentsInvocations) {
            supervisorBuilder.maxAgentsInvocations(3);
        }

        TypedBankerAgent bankSupervisor = supervisorBuilder.build();

        String userRequest = "Transfer 100 EUR from Mario's account to Georgios' one";
        TransactionDetails result = bankSupervisor.execute(userRequest);
        System.out.println(result);

        assertThat(result.fromUser()).isEqualTo("Mario");
        assertThat(result.toUser()).isEqualTo("Georgios");
        assertThat(result.amountInUSD()).isCloseTo(115.0, offset(0.1));

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);
    }

    private static Map<String, Object> toMap(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public interface TypedBankerAgentWithMemory extends ChatMemoryAccess {

        @Agent
        TransactionDetails execute(@MemoryId String memoryId, @V("request") String request);
    }

    @Test
    void typed_banker_with_memory_test() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        AtomicReference<Object> memoryId = new AtomicReference<>();
        AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        WithdrawAgent withdrawAgent = AgenticServices.agentBuilder(WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();
        CreditAgent creditAgent = AgenticServices.agentBuilder(CreditAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        ExchangeAgent exchangeAgent = AgenticServices.agentBuilder(ExchangeAgent.class)
                .chatModel(baseModel())
                .description(
                        "A money exchanger that converts a given amount of money from the original to the target currency")
                .tools(new ExchangeTool())
                .build();

        var supervisorBuilder = AgenticServices.supervisorBuilder(TypedBankerAgentWithMemory.class)
                .chatModel(plannerModel())
                .chatMemoryProvider(id -> {
                    memoryId.set(id);
                    ChatMemory mem = MessageWindowChatMemory.withMaxMessages(20);
                    if (id.equals("1")) {
                        chatMemory.set(mem);
                    }
                    return mem;
                })
                .output(agenticScope -> new TransactionDetails(
                        agenticScope.readState("withdrawUser", ""),
                        agenticScope.readState("creditUser", ""),
                        agenticScope.readState("amountInUSD", 0.0)))
                .subAgents(withdrawAgent, creditAgent, exchangeAgent);

        TypedBankerAgentWithMemory bankSupervisor = supervisorBuilder.build();

        String userRequest = "Transfer 100 EUR from Mario's account to Georgios' one";
        TransactionDetails result = bankSupervisor.execute("1", userRequest);
        System.out.println(result);

        assertThat(result.fromUser()).isEqualTo("Mario");
        assertThat(result.toUser()).isEqualTo("Georgios");
        assertThat(result.amountInUSD()).isCloseTo(115.0, offset(0.1));

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);

        assertThat(memoryId.get()).isEqualTo("1");
        ChatMemory supervisorChatMemory = bankSupervisor.getChatMemory("1");
        assertThat(chatMemory.get()).isSameAs(supervisorChatMemory);

        List<ChatMessage> messages = supervisorChatMemory.messages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        assertThat(lastMessage).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) lastMessage).text()).contains("done");
    }

    public interface GeneralAssistant extends ChatMemoryAccess {

        @UserMessage("{{userMessage}}")
        @Agent(name = "SeriousAgent", description = "Use me for serious non-joking questions")
        String respond(@MemoryId String id, @V("userMessage") String userMessage);
    }

    public interface JokesterAssistant extends ChatMemoryAccess {

        @SystemMessage("You are a jokester. You are funny, yet helpful ai assistant")
        @UserMessage("{{userMessage}}")
        @Agent(name = "JokesterAgent", description = "A fun assistant that specializes in telling jokes")
        String respond(@MemoryId String id, @V("userMessage") String userMessage);
    }

    public interface Supervisor extends ChatMemoryAccess {

        @Agent
        String respond(@MemoryId String id, @V("request") String userMessage);
    }

    @Test
    void subagent_unique_name_test() {
        JokesterAssistant jokesterAssistant = AgenticServices.agentBuilder(JokesterAssistant.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        GeneralAssistant generalAssistant = AgenticServices.agentBuilder(GeneralAssistant.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        Supervisor supervisorAgent = AgenticServices.supervisorBuilder(Supervisor.class)
                .subAgents(generalAssistant, jokesterAssistant)
                .chatModel(plannerModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .build();

        String lionJoke1 = supervisorAgent.respond("supervisor", "Tell me a joke about lions");
        assertThat(lionJoke1).isNotNull().containsIgnoringCase("lion");

        // Simulate recreating the same functional Supervisor System, same memory and all, new unique names will be generated
        JokesterAssistant jokesterAssistant2 = AgenticServices.agentBuilder(JokesterAssistant.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        GeneralAssistant generalAssistant2 = AgenticServices.agentBuilder(GeneralAssistant.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        Supervisor supervisorAgent2 = AgenticServices.supervisorBuilder(Supervisor.class)
                .subAgents(generalAssistant2, jokesterAssistant2)
                .chatModel(plannerModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .build();

        String lionJoke2 = supervisorAgent2.respond("supervisor", "tell me a joke about cheetahs");
        assertThat(lionJoke2).isNotNull().containsIgnoringCase("cheetah");

        List<ChatMessage> supervisorMessages = supervisorAgent.getChatMemory("supervisor").messages();

        Set<String> agentNames = supervisorMessages.stream()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .map(AiMessage::text)
                .map(text -> {
                    int start = text.indexOf("\"agentName\":") + "agentName:".length();
                    int agentNameStart = text.indexOf('"', start + 1)+1;
                    return text.substring(agentNameStart, text.indexOf('"', agentNameStart + 1));
                }).collect(Collectors.toSet());

        // only 2 agents, the jokester and done
        assertThat(agentNames)
                .hasSize(2)
                .containsExactly("JokesterAgent$1", "done");
    }

    @Test
    void list_argument_test() {
        ColorExpert colorExpert = AgenticServices.agentBuilder(ColorExpert.class)
                .chatModel(baseModel())
                .build();
        ColorMixerExpert colorMixerExpert = AgenticServices.agentBuilder(ColorMixerExpert.class)
                .chatModel(baseModel())
                .build();

        SupervisorAgent colorSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(colorExpert, colorMixerExpert)
                .build();

        String result = colorSupervisor.invoke("Which color do you get by mixing the color of blood and the color of the sky?");
        assertThat(result).containsIgnoringCase("purple");
    }

    @Test
    void pojo_argument_test() {
        LoanApplicationExtractor loanApplicationExtractor = AgenticServices.agentBuilder(LoanApplicationExtractor.class)
                .chatModel(baseModel())
                .outputKey("loanApplication")
                .build();
        LoanApplicationEvaluator loanApplicationEvaluator = AgenticServices.agentBuilder(LoanApplicationEvaluator.class)
                .chatModel(baseModel())
                .build();

        SupervisorAgent loanAgent = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(loanApplicationExtractor, loanApplicationEvaluator)
                .build();

        String result = loanAgent.invoke("John Doe submitted a loan application of 80000. He is 30 years old. Evaluate his application.");
        assertThat(result).containsIgnoringCase("rejected");
    }

    static class PlannerModelReturningDoneWithoutResponse implements ChatModel {

        @Override
        public ChatResponse chat(ChatRequest request) {
            String json = """
                    {
                      "agentName": "done",
                      "arguments": {}
                    }
                    """;

            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(json))
                    .build();
        }
    }

    @Test
    void supervisor_should_not_throw_when_done_has_no_response_argument() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);

        WithdrawAgent withdrawAgent = AgenticServices.agentBuilder(WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(new PlannerModelReturningDoneWithoutResponse())
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(withdrawAgent)
                .build();

        assertDoesNotThrow(() -> supervisor.invoke("Withdraw 100 USD from Mario's account"));
    }

}
