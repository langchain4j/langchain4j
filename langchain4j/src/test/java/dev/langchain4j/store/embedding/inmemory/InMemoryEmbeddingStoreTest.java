package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class InMemoryEmbeddingStoreTest extends EmbeddingStoreWithFilteringIT {

    @TempDir
    Path temporaryDirectory;

    EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    void should_serialize_to_and_deserialize_from_json() {

        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();

        String json = originalEmbeddingStore.serializeToJson();
        InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromJson(json);

        assertThat(deserializedEmbeddingStore.entries).isEqualTo(originalEmbeddingStore.entries);
        assertThat(deserializedEmbeddingStore.entries).isInstanceOf(CopyOnWriteArrayList.class);
    }

    @Test
    void should_serialize_to_and_deserialize_from_file() {
        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();
        Path filePath = temporaryDirectory.resolve("embedding-store.json");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> originalEmbeddingStore
                        .serializeToFile(temporaryDirectory.resolve("missing/store.json")))
                .withCauseInstanceOf(NoSuchFileException.class);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> InMemoryEmbeddingStore
                        .fromFile(temporaryDirectory.resolve("missing/store.json")))
                .withCauseInstanceOf(NoSuchFileException.class);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> InMemoryEmbeddingStore
                        .fromFile(temporaryDirectory.resolve("missing/store.json").toString()))
                .withCauseInstanceOf(NoSuchFileException.class);

        {
            originalEmbeddingStore.serializeToFile(filePath);
            InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromFile(filePath);

            assertThat(deserializedEmbeddingStore.entries)
                    .isEqualTo(originalEmbeddingStore.entries)
                    .hasSameHashCodeAs(originalEmbeddingStore.entries);
            assertThat(deserializedEmbeddingStore.entries).isInstanceOf(CopyOnWriteArrayList.class);
        }
        {
            originalEmbeddingStore.serializeToFile(filePath.toString());
            InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromFile(filePath);

            assertThat(deserializedEmbeddingStore.entries).isEqualTo(originalEmbeddingStore.entries);
            assertThat(deserializedEmbeddingStore.entries).isInstanceOf(CopyOnWriteArrayList.class);
        }
    }

    @Test
    void test_backwards_compatibility_with_0_27_1() {

        // given
        String json = "{\"entries\":[{\"id\":\"1\",\"embedding\":{\"vector\":[0.054768022,0.104786165,-0.065913685,-0.069075674,0.017212803,-0.06885399,0.03555302,0.04082373,0.029155456,-0.04245673,0.030366829,-0.060136396,0.03993156,-0.01973082,0.01351083,-0.0060404367,-0.021365594,0.05491437,-0.016950857,0.0012232962,0.028522901,0.07745116,-9.094399E-4,0.050454367,-0.0076457504,0.07723927,-0.040696833,0.003270552,0.035292096,-0.007387078,0.077891625,0.1269913,0.085453786,0.02659236,0.0730658,0.02425235,0.030395314,0.07185546,0.026113216,-0.05298909,-0.06828511,0.0012684684,-0.05573105,-0.004480159,0.0068528913,-0.0124351755,0.018844785,-0.038249034,-0.04675751,0.043541934,-0.06324473,-0.02479732,-0.05745626,0.07435205,0.06113783,0.03773324,0.028487088,-0.01786904,0.0076777954,0.05147286,-9.0918207E-4,-0.0018585962,-0.014670843,-0.009129795,0.005648249,0.060313072,-0.03790602,-0.043568347,0.05165225,-0.009294041,0.041024793,-0.01879247,-8.3805673E-4,0.0018276599,-0.025217142,0.037127305,0.0020467,0.02708336,0.04393207,-0.09163814,-0.014773986,0.019590693,0.037468616,0.053552758,0.015302328,-0.056031868,0.07754558,0.019573852,0.032816004,0.051690955,-0.077991724,-0.01542196,0.035718516,-0.07670223,-0.020917065,-0.06810443,0.0020987336,-0.09136348,0.07136104,0.15810311,-0.0066730613,-0.0012587234,-0.06464795,0.019459333,-0.046900466,0.02579953,0.0137103675,-0.05325879,-0.026784306,0.060180444,0.03859639,0.061738256,-0.053221185,-0.10123453,0.010938231,-0.06533966,0.018773919,-0.057320558,0.0376524,-0.0020998083,-0.04086588,-0.023697011,-0.022295833,0.14273274,-0.002185211,-0.010247722,-0.010886233,-2.163347E-33,-0.0019887923,-0.04658866,0.06778884,-0.050473724,0.012179551,0.031890105,0.024910973,-0.023081148,0.0055769687,-0.06280629,0.024637733,0.021953195,0.019858396,-0.02650883,0.07091947,-0.034755178,0.013254977,0.07807575,-0.01510443,-0.014010324,-0.016394442,-0.034263037,-0.029438984,0.006278317,0.09192872,0.0143957315,-0.048268195,-0.063103296,0.030082585,0.060335968,-0.035525836,-0.01921026,0.13722312,0.011650413,-0.025777852,0.047879547,-0.03408856,0.01688733,5.559453E-4,-0.032090656,0.11230193,-0.021276332,0.051897764,-0.041742835,-0.06988225,-0.05209886,-0.013510879,0.072805285,-0.027528374,8.036903E-4,0.068920836,0.0758128,-0.0116144065,-0.11180466,-0.10123462,-0.050683364,-0.05977721,-0.00831804,-0.034958646,0.014364084,-0.07322317,0.05232535,-0.015237244,0.066893056,-0.016871717,0.0041943095,0.026944693,-0.055398524,0.035033464,-0.02125077,-0.10205819,0.007697368,-0.04910181,0.037173204,-0.0165307,3.5218327E-4,-0.051539246,0.04246289,-0.08441004,-0.00862954,-0.05185705,0.026137767,-0.010335284,0.0172882,0.07953038,-0.0060050036,0.06719243,-0.0401764,-0.023693314,-0.032876965,-0.051083043,-0.08202445,-0.11295664,0.088754274,0.04570565,8.455842E-35,0.048689034,0.0077491105,0.063158736,0.0010138573,-0.09695236,0.023606414,-0.006290337,0.032673117,-0.091565154,0.055602036,-0.050785564,0.0012825363,-0.08484961,-0.041716576,0.030099303,-0.001673798,-0.04984004,-0.056247838,-0.015347466,0.11222524,-0.0059483647,-0.05579406,-0.029848108,0.0016613536,-0.044911478,0.04698068,0.0028564823,0.008320835,0.013569511,-0.02224078,-0.033440348,0.004992156,-0.06942684,-0.15362318,-0.07114606,0.0036061076,-0.025383294,-0.035691608,0.016051922,-0.008260644,0.0016610915,0.070809916,-0.03061924,0.047645606,-0.05488932,-0.09455125,0.04000989,0.13041453,-0.05763123,-0.022779023,-0.05587226,0.03842294,-0.050020527,-0.036174458,-0.023267364,0.041453008,-0.07026948,0.009523978,-0.02279846,-0.0011158983,0.02489702,-0.032630455,-0.10142199,0.03737143,0.0864444,-0.04730111,-0.029400265,-0.061682936,-0.118030876,-0.010528454,0.08780983,0.050029166,-0.012755714,-0.02532881,0.019210799,0.07881769,-0.03741514,-0.015251034,0.012825791,0.044248007,0.07190492,0.014891059,0.05390874,0.009154797,0.05853525,0.023270784,-0.063339435,0.031965937,-0.06112317,-0.053408615,-0.023484824,-0.001859717,-0.03631427,0.06266758,-0.032454588,-1.29681945E-8,-0.051838446,0.0039920593,0.013850329,0.046517164,0.03925248,0.06377298,-0.03198459,0.013000322,0.050003085,0.008043317,0.049262814,-0.07872641,-0.13963057,0.041573454,-0.080932565,0.012425371,0.11342785,-0.006739182,-0.03987922,0.04745593,-0.1427565,0.024888372,-0.0057953545,-0.039866846,0.06699491,-0.048645128,0.011313493,0.070836686,0.120413706,-0.017147427,0.07444055,0.022306474,0.050016057,0.10790243,0.032121025,-0.026579522,-0.049095534,0.038191266,-0.07642379,-0.05924324,0.0857516,-0.0011516957,0.010181808,-0.0071788756,0.016517205,0.068493955,-0.009853503,0.018722659,0.0042572846,-0.0044438913,-0.038963076,-0.03725601,-0.0041369284,0.04895909,-0.047640376,-0.028560074,0.044415012,0.010659731,0.056669142,0.02171314,0.02701331,-0.026512176,-0.056171637,-0.021965196]},\"embedded\":{\"text\":\"segment without metadata\",\"metadata\":{\"metadata\":{}}}},{\"id\":\"2\",\"embedding\":{\"vector\":[0.058379874,0.09344277,-0.03945775,-0.083346255,4.172384E-4,-0.058542248,0.07418938,0.054527678,0.009942889,-0.028339515,0.0098202145,-0.08387575,0.03507446,0.0028218338,0.026988508,0.016523475,-0.042796873,0.062152576,-0.032226127,0.025208337,0.04459742,0.07502659,0.0023716886,0.037037514,-0.012270076,0.08348183,-0.055675343,0.0057874215,0.038656395,-0.016783375,0.08176934,0.10704946,0.07857105,0.053079493,0.0566303,0.043502476,0.010093362,0.047119893,0.014350776,-0.019841176,-0.056514893,-0.021875558,-0.018034905,-0.020337405,-0.021525016,-0.014054547,0.014667528,-0.022382349,-0.011042101,0.06359725,-0.075823076,-0.029529907,-0.061732974,0.09256603,0.05498678,0.06784887,0.0057885745,-0.05647784,0.008020885,0.01813269,4.923422E-4,-0.004338121,-0.023514297,0.0073864185,0.03240766,0.031769786,-0.05811544,-0.072740264,0.014969319,-0.041859213,0.03778353,-0.03433398,0.017493049,-0.014173883,-0.019447315,0.037223455,0.0023598184,0.025123704,0.046292413,-0.15328602,0.015340266,0.031726558,0.027532665,0.043350082,0.035163913,-0.085452214,0.05226026,-0.006752807,0.0016299051,0.07696194,-0.023072192,-0.020220038,0.030653443,-0.06830844,-0.04127809,-0.044211537,3.78614E-5,-0.09415983,0.10081561,0.15496384,0.014853235,-0.0015652387,-0.080118254,0.022284856,-0.04223634,-0.007103736,0.0074942927,-0.051799186,-0.041980755,0.063472696,0.0421527,0.05668822,-0.094373934,-0.0871938,0.0123546915,-0.053579375,0.0033235922,-0.0403174,0.049645316,-0.020985449,-0.043271758,-0.015067257,-0.05868493,0.12209202,0.0035536827,-0.011580119,-0.04121028,-4.070006E-33,-0.0076266727,-0.061367992,0.077963285,-0.03360423,0.008575683,-0.005181847,-0.01792751,0.0042236485,-0.033197507,-0.067030914,0.008799873,0.0036406494,-8.667848E-4,-0.01954204,0.05829493,-0.037227925,-0.004716182,0.102281965,-0.066011645,0.02302273,-0.031706236,-0.05327134,-0.020773813,0.009067888,0.0853248,0.022987787,-0.047276232,-0.059121035,0.032552738,0.047861572,-0.03220927,-0.013940001,0.1319416,0.011228584,-0.023902532,0.049033094,-0.007912217,0.021667508,0.0036116396,-0.049954724,0.08710276,0.0068532005,0.059593022,-0.056072276,-0.06793391,-0.0030247984,-0.018270874,0.0882874,-0.01935367,0.030969756,0.08018428,0.032813434,-0.028545776,-0.08061849,-0.042335216,-0.027069034,-0.07248584,0.016491637,-0.028100796,0.033139244,-0.050290495,0.055679254,0.010046782,0.058187418,-0.011710944,0.03205539,0.046763774,-0.055273395,0.045685455,0.033555195,-0.11978047,0.018266639,-0.043609302,0.06912254,-0.008946313,0.01687095,-0.032237507,0.013300308,-0.09712338,4.952132E-4,-0.10147848,0.05157601,-0.021106062,0.011144059,0.017778244,0.0060047596,0.057723224,-0.061269917,-0.029449053,-0.024617422,-0.025355956,-0.057194524,-0.06976632,0.077090696,0.019826015,1.0224675E-33,0.05060453,0.017807676,0.072823085,-0.0036491158,-0.074131,-4.315518E-4,-0.024511628,0.06362351,-0.11114592,0.0766598,-0.03692944,0.007061205,-0.08720063,-0.055200804,0.040345456,-0.0283173,-0.020413626,-0.07577534,0.016257681,0.1136254,-0.005621349,-0.036638297,-0.052924395,0.0028618309,-0.015472691,0.034872197,0.019705402,-0.015243865,0.03271625,-0.024766104,-0.05350379,-0.031545445,-0.057887677,-0.10483516,-0.04064266,-0.009326445,-0.007734129,-0.06903333,0.017673174,-0.015703477,0.016369002,0.042970397,-0.024643308,0.056382105,-0.059417255,-0.09357907,0.049438626,0.15838999,-0.056195762,-0.047843937,-0.0430123,0.028257731,-0.032192707,-0.02091075,-0.025048982,0.06161837,-0.07347491,-0.032990273,-0.05015081,-0.02472312,-0.01875671,-0.027919687,-0.05737281,0.038768567,0.07385631,-0.0815219,-0.031678732,-0.10573522,-0.13461548,0.018559344,0.0909795,0.032460578,0.011498564,0.0039291554,0.028583216,0.045886107,-0.021703772,-0.022808114,0.009506056,0.063151985,0.047313724,0.046541058,0.057206716,0.038352728,0.044484988,0.033921722,-0.023643741,0.06596859,-0.06388584,-0.02779394,-0.04438385,0.005738406,-0.03711757,0.06635244,-0.05840735,-1.2430024E-8,-0.07637582,0.0034060306,-0.026113003,0.021957424,0.007894394,0.08474347,-0.05182956,8.554721E-4,0.045454036,0.00422091,0.059463825,-0.04864894,-0.12216703,0.022944216,-0.08345382,0.007683402,0.07891046,-0.0044849603,-0.03191432,0.05343823,-0.10980422,0.0189616,0.017497957,-0.048067387,0.04377856,-0.013376224,-0.003847128,0.0896214,0.09600368,-0.023600826,0.06967719,0.022684198,0.031953704,0.117912956,0.045051366,-0.059446935,-0.055162594,0.018231189,-0.055052582,-0.056634434,0.11333106,-0.03480101,-0.018577887,-0.011792301,0.037721526,0.09515242,-0.017816035,0.044649232,0.0046286825,-0.0069176015,-0.04956075,-0.043363437,0.0046781464,0.023735598,-0.040158838,-0.037803464,0.010636711,-0.0014302542,0.033024535,-0.0076811,0.016793815,-0.004985399,-0.053700797,0.0038624625]},\"embedded\":{\"text\":\"segment with metadata\",\"metadata\":{\"metadata\":{\"key\":\"value\"}}}}]}";

        // when
        EmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromJson(json);
        List<EmbeddingMatch<TextSegment>> matches = deserializedEmbeddingStore.findRelevant(
                embeddingModel.embed("segment without metadata").content(), 100);

        // then
        assertThat(matches).hasSize(2);

        TextSegment expectedSegment1 = TextSegment.from("segment without metadata");
        assertThat(matches.get(0).embeddingId()).isEqualTo("1");
        assertThat(matches.get(0).embedding()).isEqualTo(embeddingModel.embed(expectedSegment1).content());
        assertThat(matches.get(0).embedded()).isEqualTo(expectedSegment1);

        TextSegment expectedSegment2 = TextSegment.from("segment with metadata", Metadata.from("key", "value"));
        assertThat(matches.get(1).embeddingId()).isEqualTo("2");
        assertThat(matches.get(1).embedding()).isEqualTo(embeddingModel.embed(expectedSegment2).content());
        assertThat(matches.get(1).embedded()).isEqualTo(expectedSegment2);
    }

    @Test
    void should_merge_multiple_stores() {

        // given
        InMemoryEmbeddingStore<TextSegment> store1 = new InMemoryEmbeddingStore<>();
        TextSegment segment1 = TextSegment.from("first", Metadata.from("first-key", "first-value"));
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        store1.add("1", embedding1, segment1);

        InMemoryEmbeddingStore<TextSegment> store2 = new InMemoryEmbeddingStore<>();
        TextSegment segment2 = TextSegment.from("second", Metadata.from("second-key", "second-value"));
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        store2.add("2", embedding2, segment2);

        // when
        InMemoryEmbeddingStore<TextSegment> merged = InMemoryEmbeddingStore.merge(store1, store2);

        // then
        List<EmbeddingMatch<TextSegment>> matches = merged.findRelevant(embedding1, 100);
        assertThat(matches).hasSize(2);

        assertThat(matches.get(0).embeddingId()).isEqualTo("1");
        assertThat(matches.get(0).embedding()).isEqualTo(embedding1);
        assertThat(matches.get(0).embedded()).isEqualTo(segment1);

        assertThat(matches.get(1).embeddingId()).isEqualTo("2");
        assertThat(matches.get(1).embedding()).isEqualTo(embedding2);
        assertThat(matches.get(1).embedded()).isEqualTo(segment2);
    }

    private InMemoryEmbeddingStore<TextSegment> createEmbeddingStore() {

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        TextSegment segment = TextSegment.from("first");
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        TextSegment segmentWithMetadata = TextSegment.from("second", Metadata.from("key", "value"));
        Embedding embedding2 = embeddingModel.embed(segmentWithMetadata).content();
        embeddingStore.add(embedding2, segmentWithMetadata);

        return embeddingStore;
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
