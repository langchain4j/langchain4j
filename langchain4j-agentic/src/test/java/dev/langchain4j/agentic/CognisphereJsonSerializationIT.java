package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.cognisphere.CognisphereSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CognisphereJsonSerializationIT {

    private static final String COGNISPHERE_JSON = """
            {
               "key":{
                  "agentId":"ExpertRouterAgentWithMemory@1",
                  "memoryId":"1"
               },
               "kind":"PERSISTENT",
               "state":{
                  "request":"I broke my leg, what should I do?",
                  "response":"BROKEN_LEG_RESPONSE",
                  "category":{
                     "dev.langchain4j.agentic.Agents$RequestCategory":"MEDICAL"
                  }
               },
               "agentsCalls":{
                  "classify":[
                     {
                        "agentName":"classify",
                        "input":[
                           "I broke my leg, what should I do?"
                        ],
                        "output":{
                           "dev.langchain4j.agentic.Agents$RequestCategory":"MEDICAL"
                        }
                     }
                  ],
                  "medical":[
                     {
                        "agentName":"medical",
                        "input":[
                           "1",
                           "I broke my leg, what should I do?"
                        ],
                        "output":"BROKEN_LEG_RESPONSE"
                     }
                  ],
                  "invoke":[
                     {
                        "agentName":"invoke",
                        "input":[
                           {
                              "request":"I broke my leg, what should I do?",
                              "response":"BROKEN_LEG_RESPONSE",
                              "category":{
                                 "dev.langchain4j.agentic.Agents$RequestCategory":"MEDICAL"
                              }
                           }
                        ]
                     }
                  ]
               },
               "context":[
                  {
                     "agentName":"classify",
                     "message":{
                        "contents":[
                           {
                              "text":"Categorize a user request",
                              "type":"TEXT"
                           }
                        ],
                        "type":"USER"
                     }
                  },
                  {
                     "agentName":"classify",
                     "message":{
                        "text":"MEDICAL",
                        "toolExecutionRequests":[
            
                        ],
                        "attributes":{
            
                        },
                        "type":"AI"
                     }
                  },
                  {
                     "agentName":"medical",
                     "message":{
                        "contents":[
                           {
                              "text":"You are a medical expert.\\nAnalyze the following user request under a medical point of view and provide the best possible answer.\\nThe user request is I broke my leg, what should I do?.\\n",
                              "type":"TEXT"
                           }
                        ],
                        "type":"USER"
                     }
                  },
                  {
                     "agentName":"medical",
                     "message":{
                        "text":"BROKEN_LEG_RESPONSE",
                        "toolExecutionRequests":[
            
                        ],
                        "attributes":{
            
                        },
                        "type":"AI"
                     }
                  }
               ]
            }
            """;

    @Test
    void cognisphere_serialization_test() {
        DefaultCognisphere cognisphere = CognisphereSerializer.fromJson(COGNISPHERE_JSON);
        assertThat(cognisphere.memoryId()).isEqualTo("1");

        assertThat(cognisphere.readState("request")).isEqualTo("I broke my leg, what should I do?");
        assertThat(cognisphere.readState("response")).isEqualTo("BROKEN_LEG_RESPONSE");
        assertThat(cognisphere.readState("category", Agents.RequestCategory.UNKNOWN)).isEqualTo(Agents.RequestCategory.MEDICAL);

        assertThat(cognisphere.context()).hasSize(4);

        assertThat(CognisphereSerializer.toJson(cognisphere)).isEqualToIgnoringWhitespace(COGNISPHERE_JSON);
    }
}
