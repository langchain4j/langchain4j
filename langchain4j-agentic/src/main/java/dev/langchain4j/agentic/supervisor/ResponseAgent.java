package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ResponseAgent {

    @UserMessage("""
           You are a response evaluator that is provided with two responses to a user request.
           Your role is to score the two responses based on their relevance for the user request.
           
           For each of the two responses, response1 and response2, you will return a score, respectively score 1 and score 2,
           between 0.0 and 1.0, where 0.0 means the response is completely irrelevant to the user request,
           and 1.0 means the response is perfectly relevant to the user request.
           
           Return only the score and nothing else, without any additional text or explanation.

           The user request is: '{{request}}'.
           The first response is: '{{response1}}'.
           The second response is: '{{response2}}'.
           """)
    ResponseScore scoreResponses(@V("request") String request, @V("response1") String response1, @V("response2") String response2);
}
