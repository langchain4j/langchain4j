package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.RouterAgent;
import dev.langchain4j.agentic.Agents.TechnicalExpert;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class SupervisorAgentIT {

    @Test
    void tools_as_agents_tests() {
        // Nothing conceptually new, just test that now we can use AiServices as tools

        MedicalExpert medicalExpert = spy(AiServices.builder(MedicalExpert.class)
                .chatModel(baseModel())
                .build());
        LegalExpert legalExpert = spy(AiServices.builder(LegalExpert.class)
                .chatModel(baseModel())
                .build());
        TechnicalExpert technicalExpert = spy(AiServices.builder(TechnicalExpert.class)
                .chatModel(baseModel())
                .build());

        RouterAgent routerAgent = AiServices.builder(RouterAgent.class)
                .chatModel(baseModel())
                .tools(medicalExpert, legalExpert, technicalExpert)
                .build();

        System.out.println(routerAgent.askToExpert("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    public interface RequestClassifierAgent {

        @UserMessage("""
            Categorize the user request returning only one word among 'legal', 'medical' or 'technical',
            and nothing else, avoiding any explanation.
            
            The user request is: '{{request}}'.
            """)
        @Agent("An agent that categorizes the user request")
        String categorizeRequest(@V("request") String request);
    }

    @Test
    void agents_system_test() {
        // All agents are registered in the AgentsSystem, which internally uses a planner agent that can invoke other agents

        RequestClassifierAgent requestClassifierAgent = AgenticServices.agentBuilder(RequestClassifierAgent.class)
                .chatModel(baseModel())
                .build();
        MedicalExpert medicalExpert = AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .build();
        LegalExpert legalExpert = AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .build();
        TechnicalExpert technicalExpert = AgenticServices.agentBuilder(TechnicalExpert.class)
                .chatModel(baseModel())
                .build();

        SupervisorAgent askToExpert = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .subAgents(requestClassifierAgent, medicalExpert, legalExpert, technicalExpert)
                .build();

        System.out.println(askToExpert.invoke("I broke my leg what should I do"));
    }

    public interface BankerAgent {

        @UserMessage("""
            You are a banker that executes user request crediting or withdrawing US dollars (USD) from an account,
            using the tools provided and returning the final balance.
            
            The user request is: '{{it}}'.
            """)
        String execute(@P("request") String request);
    }

    public interface WithdrawAgent {
        @SystemMessage("""
            You are a banker that can only withdraw US dollars (USD) from a user account.
            """)
        @UserMessage("""
            Withdraw {{amountInUSD}} USD from {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that withdraw USD from an account")
        String withdraw(@V("user") String user, @V("amountInUSD") Double amount);
    }

    public interface CreditAgent {
        @SystemMessage("""
            You are a banker that can only credit US dollars (USD) to a user account.
            """)
        @UserMessage("""
            Credit {{amountInUSD}} USD to {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that credit USD to an account")
        String credit(@V("user") String user, @V("amountInUSD") Double amount);
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
        @UserMessage("""
            You are an operator exchanging money in different currencies.
            Use the tool to exchange {{amount}} {{originalCurrency}} into {{targetCurrency}}
            returning only the final amount provided by the tool as it is and nothing else.
            """)
        @Agent(description = "A money exchanger that converts a given amount of money from the original to the target currency",
                outputName = "exchange")
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

    public class ExchangeOperator {
        public static Map<String, Double> exchangeRatesToUSD = new HashMap<>();

        static {
            exchangeRatesToUSD.put("USD", 1.0);
            exchangeRatesToUSD.put("EUR", 1.15);
            exchangeRatesToUSD.put("CHF", 1.25);
            exchangeRatesToUSD.put("CAN", 0.8);
        }

        @Agent(description = "A money exchanger that converts a given amount of money from the original to the target currency",
                outputName = "exchange")
        public Double exchange(@V("originalCurrency") String originalCurrency, @V("amount") Double amount, @V("targetCurrency") String targetCurrency) {
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
        agentic_banker_with_exchange_test(true);
    }

    @Test
    void agentic_banker_with_non_agentic_exchange_test() {
        agentic_banker_with_exchange_test(false);
    }

    @Test
    void agentic_banker_with_italian_request_test() {
        agentic_banker_with_exchange_test(false, "Trasferisci 100 EUR dal conto di Mario a quello di Georgios");
    }

    private void agentic_banker_with_exchange_test(boolean fullyAI) {
        agentic_banker_with_exchange_test(fullyAI, "Transfer 100 EUR from Mario's account to Georgios' one");
    }

    private void agentic_banker_with_exchange_test(boolean fullyAI, String userRequest) {
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

        Object exchange;

        if (fullyAI) {
            // Using an AI agent
            exchange = AgenticServices.agentBuilder(ExchangeAgent.class)
                    .chatModel(baseModel())
                    .tools(new ExchangeTool())
                    .build();
        } else {
            // Using a non-AI agent
            exchange = new ExchangeOperator();
        }

        SupervisorAgent bankSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY)
                .subAgents(withdrawAgent, creditAgent, exchange)
                .build();

        ResultWithAgenticScope<String> result = bankSupervisor.invokeWithAgenticScope(userRequest);
        System.out.println(result.result());

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);

        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.1));
    }
}
