package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import dev.langchain4j.agentic.SupervisorAgentIT.BankTool;
import dev.langchain4j.agentic.SupervisorAgentIT.ExchangeTool;
import dev.langchain4j.agentic.declarative.AgentRegistrySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentRegistry;
import dev.langchain4j.agentic.planner.InMemoryAgentRegistry;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class AgentRegistryIT {

    @Test
    void supervisor_should_use_agent_provided_by_registry() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        SupervisorAgentIT.WithdrawAgent withdrawAgent = AgenticServices.agentBuilder(SupervisorAgentIT.WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();
        SupervisorAgentIT.CreditAgent creditAgent = AgenticServices.agentBuilder(SupervisorAgentIT.CreditAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        SupervisorAgentIT.ExchangeAgent exchangeAgent = AgenticServices.agentBuilder(SupervisorAgentIT.ExchangeAgent.class)
                .chatModel(baseModel())
                .description("A money exchanger that converts a given amount of money from the original to the target currency")
                .tools(new ExchangeTool())
                .build();

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(exchangeAgent);

        SupervisorAgent bankSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .subAgents(withdrawAgent, creditAgent)
                .agentRegistry(registry)
                .build();

        ResultWithAgenticScope<String> result = bankSupervisor
                .invokeWithAgenticScope("Transfer 100 EUR from Mario's account to Georgios' one");
        System.out.println(result.result());

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);
        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.1));
    }

    @Test
    void supervisor_should_discover_agent_added_mid_execution() {
        BankTool bankTool = new BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        SupervisorAgentIT.WithdrawAgent withdrawAgent = AgenticServices.agentBuilder(SupervisorAgentIT.WithdrawAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();
        SupervisorAgentIT.CreditAgent creditAgent = AgenticServices.agentBuilder(SupervisorAgentIT.CreditAgent.class)
                .chatModel(baseModel())
                .tools(bankTool)
                .build();

        SupervisorAgentIT.ExchangeAgent exchangeAgent = AgenticServices.agentBuilder(SupervisorAgentIT.ExchangeAgent.class)
                .chatModel(baseModel())
                .description("A money exchanger that converts a given amount of money from the original to the target currency")
                .tools(new ExchangeTool())
                .build();

        AgentRegistry dynamicRegistry = scope -> {
            if (!scope.hasState("exchange")) {
                return List.of((AgentInstance) exchangeAgent);
            }
            return List.of();
        };

        SupervisorAgent bankSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .maxAgentsInvocations(6)
                .subAgents(withdrawAgent, creditAgent)
                .agentRegistry(dynamicRegistry)
                .build();

        ResultWithAgenticScope<String> result = bankSupervisor
                .invokeWithAgenticScope("Transfer 100 EUR from Mario's account to Georgios' one");
        System.out.println(result.result());

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);
    }

    // --- Declarative API test ---

    static BankTool declarativeBankTool = new BankTool();

    public interface DeclarativeWithdrawAgent {
        @SystemMessage("You are a banker that can only withdraw US dollars (USD) from a user account.")
        @UserMessage("Withdraw {{amountInUSD}} USD from {{withdrawUser}}'s account and return the new balance.")
        @Agent("A banker that withdraw USD from an account")
        String withdraw(@V("withdrawUser") String withdrawUser, @V("amountInUSD") Double amountInUSD);

        @ToolsSupplier
        static Object tools() {
            return declarativeBankTool;
        }
    }

    public interface DeclarativeCreditAgent {
        @SystemMessage("You are a banker that can only credit US dollars (USD) to a user account.")
        @UserMessage("Credit {{amountInUSD}} USD to {{creditUser}}'s account and return the new balance.")
        @Agent("A banker that credit USD to an account")
        String credit(@V("creditUser") String creditUser, @V("amountInUSD") Double amountInUSD);

        @ToolsSupplier
        static Object tools() {
            return declarativeBankTool;
        }
    }

    public interface DeclarativeExchangeAgent {
        @UserMessage("""
            You are an operator exchanging money in different currencies.
            Use the tool to exchange {{amount}} {{originalCurrency}} into {{targetCurrency}}
            returning only the final amount provided by the tool as it is and nothing else.
            """)
        @Agent(value = "A money exchanger that converts a given amount of money from the original to the target currency",
                outputKey = "exchange")
        Double exchange(
                @V("originalCurrency") String originalCurrency,
                @V("amount") Double amount,
                @V("targetCurrency") String targetCurrency);

        @ToolsSupplier
        static Object tools() {
            return new ExchangeTool();
        }
    }

    static InMemoryAgentRegistry declarativeRegistry = new InMemoryAgentRegistry();
    static {
        DeclarativeExchangeAgent exchangeAgent = AgenticServices.agentBuilder(DeclarativeExchangeAgent.class)
                .chatModel(baseModel())
                .build();

        declarativeRegistry.register(exchangeAgent);
    }

    public interface DeclarativeSupervisorBanker {

        @dev.langchain4j.agentic.declarative.SupervisorAgent(
                responseStrategy = SupervisorResponseStrategy.SCORED,
                subAgents = {DeclarativeWithdrawAgent.class, DeclarativeCreditAgent.class})
        ResultWithAgenticScope<String> invoke(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return plannerModel();
        }

        @AgentRegistrySupplier
        static AgentRegistry registry() {
            return declarativeRegistry;
        }
    }

    @Test
    void declarative_supervisor_should_use_agent_provided_by_registry() {
        declarativeBankTool.clearAccounts();
        declarativeBankTool.createAccount("Mario", 1000.0);
        declarativeBankTool.createAccount("Georgios", 1000.0);

        DeclarativeSupervisorBanker bankSupervisor =
                AgenticServices.createAgenticSystem(DeclarativeSupervisorBanker.class, baseModel());

        ResultWithAgenticScope<String> result =
                bankSupervisor.invoke("Transfer 100 EUR from Mario's account to Georgios' one");
        System.out.println(result.result());

        assertThat(declarativeBankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(declarativeBankTool.getBalance("Georgios")).isEqualTo(1115.0);
        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.1));
    }
}
