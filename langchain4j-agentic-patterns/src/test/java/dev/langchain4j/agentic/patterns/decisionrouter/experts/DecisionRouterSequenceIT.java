package dev.langchain4j.agentic.patterns.decisionrouter.experts;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MEDICAL_AND_LEGAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.TECHNICAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.decisionModel;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.invokedExperts;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.ExpertPipeline;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.ExpertRouter;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.LegalExpert;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MedicalExpert;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MultiExpertRouter;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.ResponseSummarizer;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.ResponseSynthesizer;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.TechnicalExpert;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionRouterSequenceIT {

    private final MedicalExpert medicalExpert = AgenticServices.agentBuilder(MedicalExpert.class)
            .chatModel(baseModel())
            .build();
    private final LegalExpert legalExpert = AgenticServices.agentBuilder(LegalExpert.class)
            .chatModel(baseModel())
            .build();
    private final TechnicalExpert technicalExpert = AgenticServices.agentBuilder(TechnicalExpert.class)
            .chatModel(baseModel())
            .build();

    @Test
    void sequence_of_router_and_summarizer() {
        ExpertRouter router = AgenticServices.plannerBuilder(ExpertRouter.class)
                .subAgents(medicalExpert, legalExpert, technicalExpert)
                .outputKey("response")
                .planner(() -> new DecisionRouterPlanner(decisionModel()))
                .build();
        ResponseSummarizer summarizer = AgenticServices.agentBuilder(ResponseSummarizer.class)
                .chatModel(baseModel())
                .build();

        ExpertPipeline pipeline = AgenticServices.sequenceBuilder(ExpertPipeline.class)
                .subAgents(router, summarizer)
                .outputKey("summary")
                .build();

        ResultWithAgenticScope<String> result = pipeline.process(TECHNICAL_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedExperts(result.agenticScope())).containsExactly("technical");
        assertThat(result.agenticScope().agentInvocations("summarize")).hasSize(1);
    }

    @Test
    void sequence_of_router_with_threshold_and_synthesizer() {
        MultiExpertRouter router = AgenticServices.plannerBuilder(MultiExpertRouter.class)
                .subAgents(medicalExpert, legalExpert, technicalExpert)
                .planner(() -> new DecisionRouterPlanner(decisionModel(), 0.5))
                .build();
        ResponseSynthesizer synthesizer = AgenticServices.agentBuilder(ResponseSynthesizer.class)
                .chatModel(baseModel())
                .build();

        ExpertPipeline pipeline = AgenticServices.sequenceBuilder(ExpertPipeline.class)
                .subAgents(router, synthesizer)
                .outputKey("answer")
                .build();

        ResultWithAgenticScope<String> result = pipeline.process(MEDICAL_AND_LEGAL_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedExperts(result.agenticScope())).containsExactly("medical", "legal");
        assertThat(result.agenticScope().agentInvocations("synthesize")).hasSize(1);
    }
}
