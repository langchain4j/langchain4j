package dev.langchain4j.agentic.patterns.cascade.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.Models.enhancedModel;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ACCEPTANCE_THRESHOLD;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.UNRELATED_REQUEST;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.decisionModel;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.invokedTiers;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.AnswerSummarizer;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ExpertAgent;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.FaqAgent;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.SupportCascade;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.SupportPipeline;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class ConfidenceCascadeSequenceIT {

    @Test
    void sequence_of_cascade_and_summarizer() {
        FaqAgent faqAgent = AgenticServices.agentBuilder(FaqAgent.class)
                .chatModel(baseModel())
                .build();
        ExpertAgent expertAgent = AgenticServices.agentBuilder(ExpertAgent.class)
                .chatModel(enhancedModel())
                .build();
        SupportCascade cascade = AgenticServices.plannerBuilder(SupportCascade.class)
                .subAgents(faqAgent, expertAgent)
                .outputKey("answer")
                .planner(() -> new ConfidenceCascadePlanner(decisionModel(), ACCEPTANCE_THRESHOLD))
                .build();
        AnswerSummarizer summarizer = AgenticServices.agentBuilder(AnswerSummarizer.class)
                .chatModel(baseModel())
                .build();

        SupportPipeline pipeline = AgenticServices.sequenceBuilder(SupportPipeline.class)
                .subAgents(cascade, summarizer)
                .outputKey("summary")
                .build();

        ResultWithAgenticScope<String> result = pipeline.process(UNRELATED_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq", "expert");
        assertThat(result.agenticScope().agentInvocations("summarize")).hasSize(1);
    }
}
