package dev.langchain4j.code.local;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

class LocalScriptExecutionToolIT {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_execute_tool() {
        LocalScriptExecutionTool tool = new LocalScriptExecutionTool();
        if (!tool.isEnvReady(LocalScriptExecutionTool.ScriptType.osascript)) {
            return;
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.chat("list all running applications in my mac");
        System.out.println(answer);
        assertThat(answer).isNotEmpty();
    }
}
