package dev.langchain4j.model.workersai.client;

/**
 * Request to complete a text.
 */
public class WorkersAiTextCompletionRequest {

    String prompt;

    /**
     * Default constructor.
     */
    public WorkersAiTextCompletionRequest() {
    }

    public WorkersAiTextCompletionRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() {
        return this.prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof WorkersAiTextCompletionRequest)) return false;
        final WorkersAiTextCompletionRequest other = (WorkersAiTextCompletionRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$prompt = this.getPrompt();
        final Object other$prompt = other.getPrompt();
        if (this$prompt == null ? other$prompt != null : !this$prompt.equals(other$prompt)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof WorkersAiTextCompletionRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $prompt = this.getPrompt();
        result = result * PRIME + ($prompt == null ? 43 : $prompt.hashCode());
        return result;
    }

    public String toString() {
        return "WorkersAiTextCompletionRequest(prompt=" + this.getPrompt() + ")";
    }
}
