package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;

public class AiServicesWithToolErrorHandlerTest extends AbstractAiServicesWithToolErrorHandlerTest {

    @Override
    protected void configureGetWeatherThrowingExceptionTool(RuntimeException e, AiServices<?> aiServiceBuilder) {
        class Tools {
            @Tool
            String getWeatherThrowingException(String ignored) {
                throw e;
            }
        }
        aiServiceBuilder.tools(new Tools());
    }

    @Override
    protected void configureGetWeatherTool(AiServices<?> aiServiceBuilder) {
        class Tools {
            @Tool
            String getWeather(String ignored) {
                return "Sunny";
            }
        }
        aiServiceBuilder.tools(new Tools());
    }
}
