package dev.langchain4j.agentic.patterns.bdi.trading;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.MarketRecommendation;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.bdi.BDIPlanner;
import dev.langchain4j.agentic.patterns.bdi.Desire;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.HedgingAgent;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.LiquidityAgent;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.MarketAnalysisAgent;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.MarketRecommendationAgent;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.HedgingStrategyDefaulter;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.RebalancingAgent;
import dev.langchain4j.agentic.patterns.bdi.trading.TradingAgents.TradingSystem;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class BDIPlannerTradingIT {

    @Test
    void bdi_trading_system() {
        MarketAnalysisAgent marketAnalysis = AgenticServices.agentBuilder(MarketAnalysisAgent.class)
                .chatModel(baseModel())
                .build();

        MarketRecommendationAgent recommendation = AgenticServices.agentBuilder(MarketRecommendationAgent.class)
                .chatModel(baseModel())
                .build();

        RebalancingAgent rebalancing = AgenticServices.agentBuilder(RebalancingAgent.class)
                .chatModel(baseModel())
                .build();

        HedgingAgent hedging = AgenticServices.agentBuilder(HedgingAgent.class)
                .chatModel(baseModel())
                .build();

        LiquidityAgent liquidity = AgenticServices.agentBuilder(LiquidityAgent.class)
                .chatModel(baseModel())
                .build();

        TradingSystem tradingSystem = AgenticServices.plannerBuilder(TradingSystem.class)
                .subAgents(marketAnalysis, recommendation, new HedgingStrategyDefaulter(), rebalancing, hedging, liquidity)
                .planner(() -> new BDIPlanner(List.of(
                        Desire.of("analyze market", 1,
                                "marketData", "recommendation",
                                MarketAnalysisAgent.class, MarketRecommendationAgent.class),
                        Desire.of("hedge risks", 2,
                                scope -> scope.hasState("recommendation")
                                        && Set.of(MarketRecommendation.SELL, MarketRecommendation.STRONG_SELL)
                                            .contains(scope.readState("recommendation")),
                                scope -> scope.hasState("hedgingStrategy"),
                                HedgingAgent.class),
                        Desire.of("rebalance portfolio", 1,
                                "recommendation", "rebalancingPlan",
                                HedgingStrategyDefaulter.class, RebalancingAgent.class),
                        Desire.of("maintain liquidity", 1,
                                "portfolio", "liquidityAssessment",
                                LiquidityAgent.class)
                )))
                .build();

        ResultWithAgenticScope<String> result = tradingSystem.trade(
                "Markets crashing, major indices down 8%, VIX at 55, multiple sell-offs triggered across sectors.",
                "60% equities, 30% bonds, 10% cash. Total value: $1M.");

        assertThat(result.result()).isNotBlank();
        assertThat(result.agenticScope().hasState("marketAnalysis")).isTrue();
        assertThat(result.agenticScope().hasState("recommendation")).isTrue();
        assertThat(result.agenticScope().hasState("rebalancingPlan")).isTrue();
        assertThat(result.agenticScope().hasState("liquidityAssessment")).isTrue();

        MarketRecommendation rec = (MarketRecommendation) result.agenticScope().readState("recommendation");
        if (rec == MarketRecommendation.SELL || rec == MarketRecommendation.STRONG_SELL) {
            assertThat(result.agenticScope().hasState("hedgingStrategy")).isTrue();
        }

        System.out.println(result.result());

        HtmlReportGenerator.generateReport(tradingSystem.agentMonitor(),
        Path.of("src", "test", "resources", "bdi-trading-report.html"));
    }
}
