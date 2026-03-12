package dev.langchain4j.model.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents an AI model with its capabilities and configuration.
 */
public class ModelInfo {
    private String id;
    private String name;
    private String family;
    private Boolean attachment;
    private Boolean reasoning;

    @JsonProperty("tool_call")
    private Boolean toolCall;

    private Object interleaved;
    private Boolean temperature;
    private String knowledge;

    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("last_updated")
    private String lastUpdated;

    private Modalities modalities;

    @JsonProperty("open_weights")
    private Boolean openWeights;

    private Cost cost;
    private Limit limit;

    @JsonProperty("structured_output")
    private Boolean structuredOutput;

    private String status;

    public ModelInfo() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public Boolean getAttachment() {
        return attachment;
    }

    public void setAttachment(Boolean attachment) {
        this.attachment = attachment;
    }

    public Boolean getReasoning() {
        return reasoning;
    }

    public void setReasoning(Boolean reasoning) {
        this.reasoning = reasoning;
    }

    public Boolean getToolCall() {
        return toolCall;
    }

    public void setToolCall(Boolean toolCall) {
        this.toolCall = toolCall;
    }

    public Object getInterleaved() {
        return interleaved;
    }

    public void setInterleaved(Object interleaved) {
        this.interleaved = interleaved;
    }

    public Boolean getTemperature() {
        return temperature;
    }

    public void setTemperature(Boolean temperature) {
        this.temperature = temperature;
    }

    public String getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(String knowledge) {
        this.knowledge = knowledge;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Modalities getModalities() {
        return modalities;
    }

    public void setModalities(Modalities modalities) {
        this.modalities = modalities;
    }

    public Boolean getOpenWeights() {
        return openWeights;
    }

    public void setOpenWeights(Boolean openWeights) {
        this.openWeights = openWeights;
    }

    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public Limit getLimit() {
        return limit;
    }

    public void setLimit(Limit limit) {
        this.limit = limit;
    }

    // Utility methods
    public boolean supportsReasoning() {
        return Boolean.TRUE.equals(reasoning);
    }

    public boolean supportsToolCalls() {
        return Boolean.TRUE.equals(toolCall);
    }

    public boolean supportsAttachments() {
        return Boolean.TRUE.equals(attachment);
    }

    public boolean supportsTemperature() {
        return Boolean.TRUE.equals(temperature);
    }

    public boolean hasOpenWeights() {
        return Boolean.TRUE.equals(openWeights);
    }

    public Boolean getStructuredOutput() {
        return structuredOutput;
    }

    public void setStructuredOutput(Boolean structuredOutput) {
        this.structuredOutput = structuredOutput;
    }

    public boolean isMultimodal() {
        if (modalities == null || modalities.getInput() == null) {
            return false;
        }
        return modalities.getInput().size() > 1
                || (modalities.getInput().size() == 1 && !modalities.getInput().contains("text"));
    }

    public boolean isFree() {
        return cost != null && cost.isFree();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelInfo model = (ModelInfo) o;
        return Objects.equals(id, model.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ModelInfo [id=" + id + ", name=" + name + ", family=" + family + ", attachment=" + attachment
                + ", reasoning=" + reasoning + ", toolCall=" + toolCall + ", interleaved=" + interleaved
                + ", temperature=" + temperature + ", knowledge=" + knowledge + ", releaseDate=" + releaseDate
                + ", lastUpdated=" + lastUpdated + ", modalities=" + modalities + ", openWeights=" + openWeights
                + ", cost=" + cost + ", limit=" + limit + ", structuredOutput=" + structuredOutput + ", status="
                + status + "]";
    }

    public String prettyPrint() {
        return """
	    ----------------------------------------
	    Model ID        : %s
	    Name            : %s
	    Family          : %s
	    Reasoning       : %s
	    Tool Calls      : %s
	    Attachments     : %s
	    Temperature     : %s
	    Open Weights    : %s
	    Structured Out  : %s
	    Release Date    : %s
	    Last Updated    : %s
	    Status          : %s
	    Modalities      : %s
	    Cost            : %s
	    Limit           : %s
	    ----------------------------------------
	    """
                .formatted(
                        id,
                        name,
                        family,
                        reasoning,
                        toolCall,
                        attachment,
                        temperature,
                        openWeights,
                        structuredOutput,
                        releaseDate,
                        lastUpdated,
                        status,
                        modalities,
                        cost,
                        limit);
    }
}
