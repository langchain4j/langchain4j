package dev.langchain4j.model.googleai;


/*
 * @author : rabin
 */

import java.util.HashMap;
import java.util.Map;

public class GeminiThinkingConfig {
    private Boolean includeThoughts;
    private  Integer thinkingBudget;

    private GeminiThinkingConfig(Builder builder) {
        this.includeThoughts = builder.includeThoughts;
        this.thinkingBudget = builder.thinkingBudget;
    }

    public Boolean getIncludeThoughts() {
        return includeThoughts;
    }

    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    public void setEnableThinking(Boolean includeThoughts) {
        this.includeThoughts = includeThoughts;
    }

    public void setThinkingBudget(final Integer thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new HashMap<>();
        if (includeThoughts != null) {
            json.put("includeThoughts", includeThoughts);
        }
        if (thinkingBudget != null) {
            json.put("thinkingBudget", thinkingBudget);
        }
        return json;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Boolean includeThoughts;
        private Integer thinkingBudget;

        public Builder includeThoughts(Boolean includeThoughts) {
            this.includeThoughts = includeThoughts;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public GeminiThinkingConfig build() {
            return new GeminiThinkingConfig(this);
        }
    }

}
