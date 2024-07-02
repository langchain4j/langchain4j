package dev.langchain4j.experimental.graph;

import dev.langchain4j.chain.Chain;
import dev.langchain4j.experimental.graph.model.BaseState;
import dev.langchain4j.experimental.graph.model.FINISH;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;

import java.time.Duration;
import java.util.Map;


/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
public class graphT {
    public static final Map<String,String> pro=System.getenv();
    public static final ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .modelName("gpt-3.5-turbo")
            .apiKey(pro.get("OPEN-AI-KEY"))
            .baseUrl(pro.get("OPEN-AI-BASE-URL"))
            .logResponses(false)
            .logRequests(false)
            .maxRetries(3)
            .timeout(Duration.ofMillis(7000))
            .temperature(0D)
            .maxTokens(2000)
            .tokenizer(new OpenAiTokenizer("gpt-3.5-turbo"))
            .build();

    public static void main(String[] args) {


        Chain simpleChain1 = new SimpleChain(PromptTemplate.from("你是一个数学家擅长解决数学问题"), chatLanguageModel);
        Chain simpleChain2 = new SimpleChain(PromptTemplate.from("你是一个作家比较擅长进行文学创作"), chatLanguageModel);
        Chain simpleChain3 = new SimpleChain(PromptTemplate.from("你是一个物理学家擅长解决各类的物理问题"), chatLanguageModel);
        String desc = "name: 数学家" + " description: 一个数学家擅长解决数学问题\n" +
                "name: 文学家" + " description: 一个作家比较擅长进行文学创作\n" +
                "name: 物理学家" + " description: 一个物理学家擅长解决各类的物理问题\n";

        PromptTemplate te = PromptTemplate.from("你需要通过分析用户的意图并与以下的各个模块的介绍中寻找最匹配的一项并将它的名字返回给我 不要加任何的修饰:\n"
                + desc
                + "用户的输入为:\n"
        );
        Chain routerChain = new SimpleChain(te, chatLanguageModel);
        SimpleGraph simpleGraph = new SimpleGraph(1);
        simpleGraph.addNode(NodeFactory.createToolNode("chain0", (in) -> {
            BaseState sta = new BaseState();
            sta.setGenerate((String) routerChain.execute(in));
            sta.setQuestion(((BaseState) in).getQuestion());
            return sta;
        }));
        simpleGraph.addNode(NodeFactory.createToolNode("chain1", (in) -> {
            BaseState sta = new BaseState();
            sta.setGenerate((String) simpleChain1.execute(in));
            sta.setQuestion(((BaseState) in).getQuestion());
            return sta;
        }));

        simpleGraph.addNode(NodeFactory.createToolNode("chain2", (in) -> {
            BaseState sta = new BaseState();
            sta.setGenerate((String) simpleChain2.execute(in));
            sta.setQuestion(((BaseState) in).getQuestion());
            return sta;
        }));

        simpleGraph.addNode(NodeFactory.createToolNode("chain3", (in) -> {
            BaseState sta = new BaseState();
            sta.setGenerate((String) simpleChain3.execute(in));
            sta.setQuestion(((BaseState) in).getQuestion());
            return sta;
        }));
        simpleGraph.addNode(new FINISH());
        simpleGraph.addEdge("chain0", "chain1");
        simpleGraph.addEdge("chain0", "chain2");
        simpleGraph.addEdge("chain0", "chain3");
        simpleGraph.addEdge("chain1", "finish");
        simpleGraph.addEdge("chain2", "finish");
        simpleGraph.addEdge("chain3", "finish");
        simpleGraph.addConditionEdge("chain0", (in) -> {
            if (((BaseState) in).getGenerate().equals("数学家")) {
                return "chain1";
            } else if (((BaseState) in).getGenerate().equals("文学家")) {
                return "chain2";
            }
            return "chain3";
        });
        simpleGraph.setStartNode("chain0");
        BaseState state = new BaseState();
        state.setQuestion("云想衣裳花想容 这句诗出自哪里");
        System.out.println(simpleGraph.invoke(state));
    }


}


 class SimpleChain implements Chain<BaseState, String> {
    private final PromptTemplate promptTemplate;

    private final ChatLanguageModel languageModel;

    public SimpleChain(PromptTemplate promptTemplate, ChatLanguageModel languageModel) {
        this.promptTemplate = promptTemplate;
        this.languageModel = languageModel;
    }

    @Override
    public String execute(BaseState s) {
        String template = promptTemplate.template();
        return languageModel.generate(template + "\n" + s.getQuestion());
    }
}