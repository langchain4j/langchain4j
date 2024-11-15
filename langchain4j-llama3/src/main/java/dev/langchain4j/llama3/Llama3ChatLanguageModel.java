package dev.langchain4j.llama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Llama3ChatLanguageModel extends AbstractLlama3 implements ChatLanguageModel {

    public Llama3ChatLanguageModel(String localModelPath) throws IOException {
        this(Path.of(localModelPath));
    }

    public Llama3ChatLanguageModel(final Path ggufPath) throws IOException {
        this(ggufPath, 512, 0.1f, 0.95f);
    }

    public Llama3ChatLanguageModel(Path ggufPath, int maxToken, float temperature, float scaleFactor) throws IOException {
        super(ggufPath, maxToken, temperature, scaleFactor);
    }


    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages) {
        List<ChatFormat.Message> llamaMessages = messages.stream().map(Llama3ChatLanguageModel::toLlamaMessage).toList();
        List<Integer> promptTokens = chatFormat.encodeDialogPrompt(true, llamaMessages);
        List<Integer> responseTokens = new ArrayList<>();
        Llama.generateTokens(model, model.createNewState(), 0, promptTokens, chatFormat.getStopTokens(), maxTokens, sampler, false, responseTokens::add);

        TokenUsage tokenUsage = new TokenUsage(promptTokens.size(), responseTokens.size());
        FinishReason finishReason = FinishReason.LENGTH;
        if (!responseTokens.isEmpty() && chatFormat.getStopTokens().contains(responseTokens.getLast())) {
            finishReason = FinishReason.STOP;
            responseTokens.removeLast(); // drop stop token from answer
        }

        String responseText = model.tokenizer().decode(responseTokens);
        return Response.from(AiMessage.from(responseText), tokenUsage, finishReason);

    }
}
