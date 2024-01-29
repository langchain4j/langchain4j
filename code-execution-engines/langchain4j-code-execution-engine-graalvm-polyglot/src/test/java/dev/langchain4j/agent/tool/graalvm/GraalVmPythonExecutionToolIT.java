package dev.langchain4j.agent.tool.graalvm;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GraalVmPythonExecutionToolIT {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .build();

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    public void should_execute_tool() {

        GraalVmPythonExecutionTool tool = spy(new GraalVmPythonExecutionTool());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.chat("What is the square root of 485906798473894056 in scientific notation?");

        assertThat(answer).contains("6.97");

        verify(tool).executePythonCode(contains("485906798473894056"));
    }
}