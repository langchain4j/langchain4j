package dev.langchain4j.agentic;

import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class DynamicAgentsIT {

    @Test
    void untyped_sequential_untyped_agents_tests() {
        check_sequential_untyped_agents_tests(false);
    }

    @Test
    void typed_sequential_untyped_agents_tests() {
        check_sequential_untyped_agents_tests(true);
    }

    void check_sequential_untyped_agents_tests(boolean typedSequence) {
        UntypedAgent creativeWriter = AgenticServices.agentBuilder()
                .chatModel(baseModel())
                .description("Generate a story based on the given topic")
                .userMessage("""
                        You are a creative writer.
                        Generate a draft of a story long no more than 3 sentence around the given topic.
                        Return only the story and nothing else.
                        The topic is {{topic}}.
                        """)
                .returnType(String.class) // String is the default return type for untyped agents
                .outputKey("story")
                .build();

        Agents.AudienceEditor audienceEditor = AgenticServices.agentBuilder(Agents.AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        UntypedAgent styleEditor = AgenticServices.agentBuilder()
                .chatModel(baseModel())
                .description("Edit a story to better fit a given style")
                .userMessage("""
                        You are a professional editor.
                        Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                        Return only the story and nothing else.
                        The story is "{{story}}".
                        """)
                .outputKey("story")
                .build();

        String story = null;

        if (typedSequence) {
            UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                    .subAgents(creativeWriter, audienceEditor, styleEditor)
                    .outputKey("story")
                    .build();

            Map<String, Object> input = Map.of(
                    "topic", "dragons and wizards",
                    "style", "fantasy",
                    "audience", "young adults");

            story = (String) novelCreator.invoke(input);

        } else {
            Agents.ReviewedWriter novelCreator = AgenticServices.sequenceBuilder(Agents.ReviewedWriter.class)
                    .subAgents(creativeWriter, audienceEditor, styleEditor)
                    .outputKey("story")
                    .build();

            story = novelCreator.writeStory("dragons and wizards", "young adults", "fantasy");
        }

        assertThat(story).containsIgnoringCase("dragon");
    }

    @Test
    void supervisor_with_untyped_agents_tests() {
        SupervisorAgentIT.BankTool bankTool = new SupervisorAgentIT.BankTool();
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        UntypedAgent withdrawAgent = AgenticServices.agentBuilder()
                .chatModel(baseModel())
                .systemMessage("You are a banker that can only withdraw US dollars (USD) from a user account.")
                .userMessage("Withdraw {{amountInUSD}} USD from {{withdrawUser}}'s account and return the new balance.")
                .description("A banker that withdraw USD from an account")
                .tools(bankTool)
                .inputKeys(String.class, "withdrawUser", Double.class, "amountInUSD")
                .returnType(String.class)
                .build();

        UntypedAgent creditAgent = AgenticServices.agentBuilder()
                .chatModel(baseModel())
                .systemMessage("You are a banker that can only credit US dollars (USD) to a user account.")
                .userMessage("Credit {{amountInUSD}} USD to {{creditUser}}'s account and return the new balance.")
                .description("A banker that credit USD to an account")
                .tools(bankTool)
                .inputKeys(String.class, "creditUser", Double.class, "amountInUSD")
                .returnType(String.class)
                .build();

        UntypedAgent exchangeAgent = AgenticServices.agentBuilder()
                .chatModel(baseModel())
                .description("A money exchanger that converts a given amount of money from the original to the target currency")
                .userMessage("""
                        You are an operator exchanging money in different currencies.
                        Use the tool to exchange {{amount}} {{originalCurrency}} into {{targetCurrency}}
                        returning only the final amount provided by the tool as it is and nothing else.
                        """)
                .tools(new SupervisorAgentIT.ExchangeTool())
                .inputKeys(String.class, "originalCurrency", Double.class, "amount", String.class, "targetCurrency")
                .returnType(Double.class)
                .outputKey("exchange")
                .build();

        SupervisorAgent bankSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY) // default
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(withdrawAgent, creditAgent, exchangeAgent)
                .build();

        ResultWithAgenticScope<String> result = bankSupervisor.invokeWithAgenticScope("Transfer 100 EUR from Mario's account to Georgios' one");
        System.out.println(result.result());

        assertThat(bankTool.getBalance("Mario")).isEqualTo(885.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1115.0);

        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.1));
    }
}
