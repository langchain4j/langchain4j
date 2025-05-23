package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.GoogleSearchRetrieval;
import com.google.cloud.vertexai.api.Retrieval;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.VertexAISearch;

/**
 * Ground Gemini responses with Google Search web results
 * or with Vertex AI Search datastores
 */
class ResponseGrounding {

    static Tool googleSearchTool() {
        return Tool.newBuilder()
            .setGoogleSearchRetrieval(
                GoogleSearchRetrieval.newBuilder()
//                    .setDisableAttribution(false)
                    .build())
            .build();
    }

    /**
     * @param datastore fully qualified name of the Vertex Search datastore, with the following format
     *                  "projects/PROJECT_ID/locations/global/collections/default_collection/dataStores/DATASTORE_NAME"
     */
    static Tool vertexAiSearch(String datastore) {
        return Tool.newBuilder()
            .setRetrieval(
                Retrieval.newBuilder()
                    .setVertexAiSearch(VertexAISearch.newBuilder().setDatastore(datastore))
                    .setDisableAttribution(false))
            .build();
    }
}
