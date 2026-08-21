package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;

import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Map;

public class HoroscopeMain {

    public interface AstrologyAgent {
        @SystemMessage(
                """
            You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
            """)
        @UserMessage("""
            Generate the horoscope for {{name}} who is a {{sign}}.
            """)
        @Agent("An astrologist that generates horoscopes based on the user's name and zodiac sign.")
        String horoscope(@V("name") String name, @V("sign") String sign);
    }

    public static void main(String[] args) {
        HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
                .description("An agent that asks the zodiac sign of the user")
                .outputKey("sign")
                .responseProvider(scope -> {
                    System.out.println("Hi " + scope.readState("name") + ", what is your sign?");
                    System.out.print("> ");
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        return reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read input", e);
                    }
                })
                .build();

        AstrologyAgent astrologyAgent = AgenticServices.agentBuilder(AstrologyAgent.class)
                .chatModel(baseModel())
                .outputKey("horoscope")
                .build();

        UntypedAgent horoscopeAgent = AgenticServices.sequenceBuilder()
                .subAgents(humanInTheLoop, astrologyAgent)
                .outputKey("horoscope")
                .build();

        ResultWithAgenticScope<String> horoscope =
                horoscopeAgent.invokeWithAgenticScope(Map.of("name", "Mario"));
        System.out.println("User's sign: " + horoscope.agenticScope().readState("sign"));
        System.out.println("Horoscope: " + horoscope.result());
    }
}
