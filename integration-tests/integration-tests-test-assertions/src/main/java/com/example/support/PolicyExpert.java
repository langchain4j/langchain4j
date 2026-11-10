package com.example.support;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;

@SystemMessage("You answer questions about the policies of Miles of Smiles car rental, based only on the given policies.")
public interface PolicyExpert {

    String answer(String question);

    Result<String> answerWithSources(String question);
}
