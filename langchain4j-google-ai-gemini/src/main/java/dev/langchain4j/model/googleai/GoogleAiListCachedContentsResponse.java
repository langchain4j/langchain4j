package dev.langchain4j.model.googleai;

import java.util.List;

 class GoogleAiListCachedContentsResponse {

    private List<GeminiCachedContent> cachedContents;

    public List<GeminiCachedContent> getCachedContents() {
        return cachedContents;
    }

    public void setCachedContents(final List<GeminiCachedContent> cachedContents) {
        this.cachedContents = cachedContents;
    }

     @Override
     public String toString() {
         return "GoogleAiListCachedContentsResponse{" +
                 "cachedContents=" + cachedContents +
                 '}';
     }

 }
