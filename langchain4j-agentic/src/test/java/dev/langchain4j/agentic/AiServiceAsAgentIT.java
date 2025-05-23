package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.planner.AgentsSystem;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AiServiceAsAgentIT {

    private static final String OLLAMA_BASE_URL = ollamaBaseUrl(ollama);
//    private static final String OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    static final ChatModel model = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    public interface RouterAgent {

        @UserMessage("""
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical',
            then forward the request as it is to the corresponding expert provided as a tool.
            Finally return the answer that you received from the expert without any modification.

            The user request is: '{{it}}'.
            """)
        String askToExpert(String request);
    }

    public interface MedicalExpert {

        @UserMessage("""
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A medical expert")
        @Agent("A medical expert")
        String medical(String request);
    }

    public interface LegalExpert {

        @UserMessage("""
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A legal expert")
        @Agent("A legal expert")
        String legal(String request);
    }

    public interface TechnicalExpert {

        @UserMessage("""
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A technical expert")
        @Agent("A technical expert")
        String technical(String request);
    }

    @Test
    void tools_as_agents_tests() {
        // Nothing conceptually new, just test that now we can use AiServices as tools

        MedicalExpert medicalExpert = spy(AiServices.builder(MedicalExpert.class)
                .chatModel(model)
                .build());
        LegalExpert legalExpert = spy(AiServices.builder(LegalExpert.class)
                .chatModel(model)
                .build());
        TechnicalExpert technicalExpert = spy(AiServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .build());

        RouterAgent routerAgent = AiServices.builder(RouterAgent.class)
                .chatModel(model)
                .tools(medicalExpert, legalExpert, technicalExpert)
                .build();

        System.out.println(routerAgent.askToExpert("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    public interface RequestClassifierAgent {

        @UserMessage("""
            Categorize the user request returning only one word among 'legal', 'medical' or 'technical',
            and nothing else, avoiding any explanation.
            
            The user request is: '{{it}}'.
            """)
        @Agent("An agent that categorizes the user request")
        String categorizeRequest(String request);
    }

    @Test
    void agentic_tests() {
        // All user's interactions go through the router agent, but it now can redirect the request
        // to the appropriate expert and keep track of this decision in the conversation state
        // for subsequent requests.

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // ChatState enriches the chat memory with additional state information, just a Map
        ChatState chatState = new DefaultChatState(chatMemory);

        MedicalExpert medicalExpert = spy(AgentServices.builder(MedicalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());
        LegalExpert legalExpert = spy(AgentServices.builder(LegalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());
        TechnicalExpert technicalExpert = spy(AgentServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());

        AtomicInteger routerInvocationCounter = new AtomicInteger(0);
        AtomicInteger expertInvocationCounter = new AtomicInteger(0);

        RequestClassifierAgent routerAgent = AgentServices.builder(RequestClassifierAgent.class)
                .chatModel(model)
                .chatState(chatState)
                .onRequest( request -> {
                    if (request.chatState().hasState("expertType")) {
                        expertInvocationCounter.incrementAndGet();
                        String agentName = request.chatState().readState("expertType").toString();
                        return AgentDirective.redirectTo(agentName);
                    }
                    return AgentDirective.prompt();
                })
                .onResponse( response -> {
                    if (response.agentName().equals("categorizeRequest")) {
                        routerInvocationCounter.incrementAndGet();
                        response.chatState().writeState("expertType", response.response());
                        return AgentDirective.prompt();
                    }
                    return AgentDirective.terminate();
                })
                .agents(medicalExpert, legalExpert, technicalExpert)
                .build();

        String response1 = routerAgent.categorizeRequest("I broke my leg what should I do");
        System.out.println(response1);
        verify(medicalExpert).medical("I broke my leg what should I do");

        // Both the router and the expert are invoked once
        assertThat(routerInvocationCounter.get()).isEqualTo(1);
        assertThat(expertInvocationCounter.get()).isEqualTo(1);

        String response2 = routerAgent.categorizeRequest("Which part of my body is broken?");
        System.out.println(response2);
        assertThat(response2).containsIgnoringCase("leg");

        // The request is redirected to expert so only that one is invoked
        assertThat(routerInvocationCounter.get()).isEqualTo(1);
        assertThat(expertInvocationCounter.get()).isEqualTo(2);
    }

    static final ChatModel plannerModel = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen3:8b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void agents_system_test() {
        // All agents are registered in the AgentsSystem, which internally uses a planner agent that can invoke other agents

        RequestClassifierAgent requestClassifierAgent = AgentServices.builder(RequestClassifierAgent.class)
                .chatModel(model)
                .build();
        MedicalExpert medicalExpert = AgentServices.builder(MedicalExpert.class)
                .chatModel(model)
                .build();
        LegalExpert legalExpert = AgentServices.builder(LegalExpert.class)
                .chatModel(model)
                .build();
        TechnicalExpert technicalExpert = AgentServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .build();

        AgentsSystem agentsSystem = new AgentsSystem(plannerModel, requestClassifierAgent, medicalExpert, legalExpert, technicalExpert);
        System.out.println(agentsSystem.execute("I broke my leg what should I do"));
    }

    public interface BankerAgent {

        @UserMessage("""
            You are a banker that executes user request crediting or withdrawing US dollars (USD) from an account,
            using the tools provided and returning the final balance.
            
            The user request is: '{{it}}'.
            """)
        // This is not an agentic method, so the agent system cannot invoke it
        String execute(@P("request") String request);

        @SystemMessage("""
            You are a banker that can only withdraw US dollars (USD) from a user account,
            """)
        @UserMessage("""
            Withdraw {{amount}} USD from {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that withdraw USD from an account")
        String withdraw(@V("user") String user, @V("amount") Double amount);

        @SystemMessage("""
            You are a banker that can only credit US dollars (USD) to a user account,
            """)
        @UserMessage("""
            Credit {{amount}} USD to {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that credit USD to an account")
        String credit(@V("user") String user, @V("amount") Double amount);
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
                .chatModel(model)
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

        BankerAgent bankerAgent = AgentServices.builder(BankerAgent.class)
                .chatModel(model)
                .tools(bankTool)
                .build();

        AgentsSystem agentsSystem = new AgentsSystem(plannerModel, bankerAgent);
        System.out.println(agentsSystem.execute("Transfer 100 USD from Mario's account to Georgios' one"));

        assertThat(bankTool.getBalance("Mario")).isEqualTo(900.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1100.0);
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

    static class ExchangeTool {

        public static Map<String, Double> exchangeRatesToUSD = new HashMap<>();

        static {
            exchangeRatesToUSD.put("USD", 1.0);
            exchangeRatesToUSD.put("EUR", 1.15);
            exchangeRatesToUSD.put("CHF", 1.25);
            exchangeRatesToUSD.put("CAN", 0.8);
        }

        @Tool("Exchange the given amount of money from the original to the target currency")
        Double exchange(@P("originalCurrency") String originalCurrency, @P("amount") Double amount, @P("targetCurrency") String targetCurrency) {
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
    void agentic_banker_with_exchange_test() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        BankerAgent bankerAgent = AgentServices.builder(BankerAgent.class)
                .chatModel(model)
                .tools(bankTool)
                .build();

        ExchangeAgent exchangeAgent = AgentServices.builder(ExchangeAgent.class)
                .chatModel(model)
                .tools(new ExchangeTool())
                .build();

        AgentsSystem agentsSystem = new AgentsSystem(plannerModel, bankerAgent, exchangeAgent);
        System.out.println(agentsSystem.execute("Transfer 100 EUR from Mario's account to Georgios' one"));

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);
    }
}
