package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.planner.AgentsSystem;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.AiServiceAsAgentIT.model;
import static dev.langchain4j.agentic.AiServiceAsAgentIT.plannerModel;

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
                .chatModel(model)
                .tools(new AskUserTool())
                .build();

        AstrologyAgent astrologyAgent = AgentServices.builder(AstrologyAgent.class)
                .chatModel(model)
                .build();

        AgentsSystem agentsSystem = new AgentsSystem(plannerModel, askerAgent, astrologyAgent);
        System.out.println(agentsSystem.execute("My name is Mario. What is my horoscope?"));
    }
}
