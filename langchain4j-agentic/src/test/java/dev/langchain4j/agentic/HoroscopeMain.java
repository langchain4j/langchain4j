package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static dev.langchain4j.agentic.Models.PLANNER_MODEL;

public class HoroscopeMain {

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

    public static void main(String[] args) {
        AstrologyAgent astrologyAgent = AgentServices.agentBuilder(AstrologyAgent.class)
                .chatModel(BASE_MODEL)
                .build();

        HumanInTheLoop humanInTheLoop = AgentServices.humanInTheLoopBuilder()
                .description("An agent that asks the zodiac sign of the user")
                .outputName("sign")
                .requestWriter(request -> {
                    System.out.println(request);
                    System.out.print("> ");
                })
                .responseReader(() -> System.console().readLine())
                .build();

        SupervisorAgent horoscopeAgent = AgentServices.supervisorBuilder()
                .chatModel(PLANNER_MODEL)
                .subAgents(astrologyAgent, humanInTheLoop)
                .build();

        ResultWithCognisphere<String> horoscope = horoscopeAgent.invokeWithCognisphere("My name is Mario. What is my horoscope?");
        System.out.println("User's sign: " + horoscope.cognisphere().readState("sign"));
        System.out.println("Horoscope: " + horoscope.result());
    }
}
