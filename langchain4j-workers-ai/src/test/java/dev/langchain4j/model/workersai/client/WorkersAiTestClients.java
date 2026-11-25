package dev.langchain4j.model.workersai.client;

public final class WorkersAiTestClients {

    private WorkersAiTestClients() {}

    public static <T extends AbstractWorkersAIModel> T withClient(T model, WorkersAiApi client) {
        model.workerAiClient = client;
        return model;
    }
}
