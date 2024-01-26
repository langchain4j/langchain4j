package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

public class VearchClientTest {
    private static VearchClient vearchRouterClient;
    private static String dbName;
    private static String spaceName;
    private static String commonId;

    @BeforeAll
    public static void before() {
        vearchRouterClient = new VearchClient("http://47.93.124.244:9001", Duration.ofMinutes(1));
        dbName = "ts_db";
        spaceName = "ts_space";
        commonId = "110111112";
    }

    @Test
    public void documentUpsert() {
        Gson gson = new Gson();

        List<Float> featureList = new ArrayList<>();
        featureList.add(0.014448151F);
        featureList.add(0.012726868F);
        featureList.add(0.014262066F);
        featureList.add(0.01459436F);
        featureList.add(-0.0012901322F);
        featureList.add(0.004346408F);
        featureList.add(0.016441917F);
        featureList.add(-0.00071401714F);
        featureList.add(-0.012241717F);
        featureList.add(-0.014793737F);
        featureList.add(0.012587304F);
        featureList.add(0.02181179F);
        featureList.add(-0.034239594F);
        featureList.add(-0.0104805585F);
        featureList.add(-0.026663285F);
        featureList.add(0.023260593F);
        featureList.add(0.016415333F);
        featureList.add(0.0030703982F);
        featureList.add(0.028151963F);
        featureList.add(-0.020974409F);
        featureList.add(-0.00846021F);
        featureList.add(0.022954883F);
        featureList.add(0.0027547188F);
        featureList.add(-0.009423863F);
        featureList.add(-0.010021993F);
        featureList.add(0.018581891F);
        featureList.add(0.022768797F);
        featureList.add(-0.0045923055F);
        featureList.add(-0.010520434F);
        featureList.add(0.013391456F);
        featureList.add(0.032538246F);
        featureList.add(0.031474907F);
        System.out.println(gson.toJson(featureList));


        Map<String, List<Float>> map1_1 = new HashMap<>();
        map1_1.put("feature", featureList);
        System.out.println(gson.toJson(map1_1));

        Map<String, Object> map1 = new HashMap<>();
        map1.put("_id", commonId);
        map1.put("ts_keyword", "abc");
        map1.put("ts_integer", 123);
        map1.put("ts_float", 123.23f);
        map1.put("ts_string_ary", "cbd");
        map1.put("ts_integer_index", 567);
        map1.put("ts_vector", map1_1);
//        map1.put("ts_vector_ext", map1_1);

        List<Map<String, Object>> documentsList = new ArrayList<>();
        documentsList.add(map1);

        DocumentUpsertRequest request = new DocumentUpsertRequest();
        request.setDbName(dbName);
        request.setSpaceName(spaceName);
        request.setDocuments(documentsList);

        System.out.println(gson.toJson(request));

        JsonObject ret = vearchRouterClient.documentUpsert(request);
        System.out.println("ret = " + ret);
    }

    @Test
    public void documentSearch_vearch() {
        Gson gson = new Gson();

        List<Float> feature = new ArrayList<>();
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);

        DocumentSearchRequest.Vector vector = new DocumentSearchRequest.Vector();
        vector.setField("ts_vector");
        vector.setMinScore(0.9);
        vector.setFeature(feature);

        List<DocumentSearchRequest.Vector> vectorList = new ArrayList<>();
        vectorList.add(vector);

        DocumentSearchRequest.Query query = new DocumentSearchRequest.Query();
        query.setVector(vectorList);

        DocumentSearchRequest request = new DocumentSearchRequest();
        request.setDbName(dbName);
        request.setSpaceName(spaceName);
        request.setQuery(query);
        request.setSize(3);
        request.setVectorValue(Boolean.TRUE);

        System.out.println("request = " + gson.toJson(request));

        JsonObject ret = vearchRouterClient.documentSearch(request);
        System.out.println("ret = " + ret);
    }

}
