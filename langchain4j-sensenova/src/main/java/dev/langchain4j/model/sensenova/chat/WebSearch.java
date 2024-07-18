package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public final class WebSearch {

    /**
     * intput
     */
	@SerializedName("search_enable")
	private Boolean searchEnable;

    /**
     * input
     */
	@SerializedName("result_enable")
	private Boolean resultEnable;


    /**
     * output
     */
    @SerializedName("online_search_count")
	private int onlineSearchCount;

    /**
     * output
     */
	private List<WebSearchResult> results;

}