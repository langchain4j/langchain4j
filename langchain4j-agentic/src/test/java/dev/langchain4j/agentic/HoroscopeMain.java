package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.supervisor.PromptAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static dev.langchain4j.agentic.Models.PLANNER_MODEL;

public class HoroscopeMain {
    public interface UserAskerAgent {
        @SystemMessage("""
            You are an operator asking the user for information and returning the answer as it is.
            """)
        @UserMessage("""
            Use the tool to ask {{request}} to the user and return the answer as it is.
            """)
        @Agent("An agent that asks the user for information")
        String askUser(@V("request") String request);
    }

    static class AskUserTool {

        @Tool("Ask information to the user")
        String askUser(@P("request") String request) {
            // Read user input from the standard input and return it
            System.out.println(request);
            System.out.print("> ");
            return System.console().readLine();
        }
    }

    public interface AstrologyAgent {
        @SystemMessage("""
            You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
            """)
        @UserMessage("""
            Generate the horoscope for {{name}} who is a {{sign}}.
            """)
        @Agent("an astrologist that generates horoscopes based on the user's name and zodiac sign.")
        String horoscope(@V("name") String name, @V("sign") String sign);
    }

    public static void main(String[] args) {
        UserAskerAgent askerAgent = AgentServices.builder(UserAskerAgent.class)
                .chatModel(BASE_MODEL)
                .tools(new AskUserTool())
                .build();

        AstrologyAgent astrologyAgent = AgentServices.builder(AstrologyAgent.class)
                .chatModel(BASE_MODEL)
                .build();

        PromptAgent horoscopeAgent = SupervisorAgentService.builder(PLANNER_MODEL)
                .subAgents(askerAgent, astrologyAgent)
                .build();

        System.out.println(horoscopeAgent.process("My name is Mario. What is my horoscope?"));
    }
}
