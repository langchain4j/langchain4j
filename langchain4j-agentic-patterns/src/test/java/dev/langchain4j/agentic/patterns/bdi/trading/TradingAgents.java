package dev.langchain4j.agentic.patterns.bdi.trading;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class TradingAgents {

    public enum MarketRecommendation {
        STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    }

    public interface MarketAnalysisAgent {

        @UserMessage("""
                You are a market analyst. Analyze the following market data and portfolio state.
                Provide a concise assessment of the current market conditions, risks, and opportunities.
                Market data: {{marketData}}
                Portfolio: {{portfolio}}
                """)
        @Agent(value = "Analyze market conditions and identify risks/opportunities", outputKey = "marketAnalysis")
        String analyzeMarket(@V("marketData") String marketData, @V("portfolio") String portfolio);
    }

    public interface MarketRecommendationAgent {

        @UserMessage("""
                You are a financial advisor. Based on the following market analysis,
                provide a single trading recommendation.
                Market analysis: {{marketAnalysis}}
                """)
        @Agent(value = "Provide a trading recommendation based on market analysis", outputKey = "recommendation")
        MarketRecommendation recommend(@V("marketAnalysis") String marketAnalysis);
    }

    public static class HedgingStrategyDefaulter {
        @Agent(outputKey = "hedgingStrategy")
        public String defaultHedging(AgenticScope scope) {
            return scope.hasState("hedgingStrategy") ? (String) scope.readState("hedgingStrategy") : "None";
        }
    }

    public interface RebalancingAgent {

        @UserMessage("""
                You are a portfolio rebalancing specialist.
                Based on the market analysis, suggest portfolio adjustments to maximize returns.
                Keep suggestions concise and actionable.
                Market analysis: {{marketAnalysis}}
                Hedging strategy in place: {{hedgingStrategy}}
                Portfolio: {{portfolio}}
                """)
        @Agent(value = "Suggest portfolio rebalancing to maximize returns", outputKey = "rebalancingPlan")
        String rebalance(@V("marketAnalysis") String marketAnalysis,
                         @V("hedgingStrategy") String hedgingStrategy,
                         @V("portfolio") String portfolio);
    }

    public interface HedgingAgent {

        @UserMessage("""
                You are a risk management specialist.
                Based on the market analysis, recommend hedging strategies to minimize risk exposure.
                Focus on protecting against identified threats.
                Market analysis: {{marketAnalysis}}
                """)
        @Agent(value = "Recommend hedging strategies to minimize risk", outputKey = "hedgingStrategy")
        String hedge(@V("marketAnalysis") String marketAnalysis);
    }

    public interface LiquidityAgent {

        @UserMessage("""
                You are a liquidity management specialist.
                Based on the portfolio state, assess current liquidity and recommend actions
                to maintain adequate cash reserves.
                Portfolio: {{portfolio}}
                """)
        @Agent(value = "Assess and maintain portfolio liquidity", outputKey = "liquidityAssessment")
        String assessLiquidity(@V("portfolio") String portfolio);
    }

    public interface TradingSystem extends MonitoredAgent {

        @Agent
        ResultWithAgenticScope<String> trade(@V("marketData") String marketData, @V("portfolio") String portfolio);

        @Output
        static String trade(@V("marketAnalysis") String marketAnalysis,
                            @V("recommendation") MarketRecommendation recommendation,
                            @V("rebalancingPlan") String rebalancingPlan,
                            @V("liquidityAssessment") String liquidityAssessment,
                            AgenticScope scope) {
            String hedging = scope.hasState("hedgingStrategy")
                    ? (String) scope.readState("hedgingStrategy")
                    : "Not needed (recommendation: " + recommendation + ")";
            return "Trading system output:\n----\n" +
                    "Market Analysis: " + marketAnalysis + "\n----\n" +
                    "Recommendation: " + recommendation + "\n----\n" +
                    "Hedging Strategy: " + hedging + "\n----\n" +
                    "Rebalancing Plan: " + rebalancingPlan + "\n----\n" +
                    "Liquidity Assessment: " + liquidityAssessment;
        }
    }
}
