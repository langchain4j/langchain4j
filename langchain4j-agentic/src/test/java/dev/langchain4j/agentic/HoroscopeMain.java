package dev.langchain4j.agentic;

import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;

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
        AstrologyAgent astrologyAgent = AgenticServices.agentBuilder(AstrologyAgent.class)
                .chatModel(baseModel())
                .build();

        HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
                .description("An agent that asks the zodiac sign of the user")
                .outputName("sign")
                .requestWriter(request -> {
                    System.out.println(request);
                    System.out.print("> ");
                })
                .responseReader(() -> System.console().readLine())
                .build();

        SupervisorAgent horoscopeAgent = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .subAgents(astrologyAgent, humanInTheLoop)
                .build();

        ResultWithAgenticScope<String> horoscope = horoscopeAgent.invokeWithAgenticScope("My name is Mario. What is my horoscope?");
        System.out.println("User's sign: " + horoscope.agenticScope().readState("sign"));
        System.out.println("Horoscope: " + horoscope.result());
    }
}
