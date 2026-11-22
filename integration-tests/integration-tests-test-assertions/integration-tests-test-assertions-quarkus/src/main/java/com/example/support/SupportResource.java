package com.example.support;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/support")
@Produces(MediaType.TEXT_PLAIN)
public class SupportResource {

    private final CustomerSupportAssistant assistant;
    private final SupportService supportService;

    public SupportResource(CustomerSupportAssistant assistant, SupportService supportService) {
        this.assistant = assistant;
        this.supportService = supportService;
    }

    @GET
    @Path("/chat")
    public String chat(@QueryParam("q") String question) {
        return assistant.chat(question);
    }

    @GET
    @Path("/chat-async")
    public Uni<String> chatAsync(@QueryParam("q") String question) {
        return supportService.answerLater(question);
    }

    @GET
    @Path("/chat-stream")
    public Multi<String> chatStream(@QueryParam("q") String question) {
        return supportService.streamAnswer(question);
    }
}
