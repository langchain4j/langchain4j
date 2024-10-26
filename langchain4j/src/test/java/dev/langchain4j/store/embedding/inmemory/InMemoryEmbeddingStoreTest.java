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

        assertThat(deserializedEmbeddingStore.entries)
                .isEqualTo(originalEmbeddingStore.entries)
                .isInstanceOf(CopyOnWriteArrayList.class);
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

            assertThat(deserializedEmbeddingStore.entries)
                    .isEqualTo(originalEmbeddingStore.entries)
                    .isInstanceOf(CopyOnWriteArrayList.class);
        }
    }

    @Test
    void test_backwards_compatibility_with_0_27_1() {

        // given
        String json = "{\"entries\":[{\"id\":\"1\",\"embedding\":{\"vector\":[0.05476802, 0.10478615, -0.06591367, -0.069075674, 0.017212812, -0.06885398, 0.03555302, 0.04082374, 0.02915545, -0.042456735, 0.030366832, -0.060136393, 0.03993154, -0.01973082, 0.01351083, -0.006040437, -0.0213656, 0.054914378, -0.016950857, 0.0012232928, 0.0285229, 0.07745115, -9.0944575E-4, 0.050454363, -0.0076457513, 0.07723925, -0.04069683, 0.0032705476, 0.0352921, -0.0073870677, 0.07789161, 0.1269913, 0.08545379, 0.026592365, 0.073065795, 0.02425234, 0.030395307, 0.07185546, 0.026113214, -0.052989088, -0.06828511, 0.0012684639, -0.055731047, -0.00448016, 0.0068528843, -0.012435178, 0.018844783, -0.038249027, -0.04675751, 0.04354193, -0.06324474, -0.024797317, -0.057456255, 0.07435204, 0.061137825, 0.037733246, 0.028487086, -0.017869039, 0.0076777977, 0.05147286, -9.0917747E-4, -0.0018585982, -0.014670835, -0.00912981, 0.005648249, 0.060313076, -0.037906017, -0.04356834, 0.05165225, -0.0092940405, 0.04102479, -0.01879247, -8.380583E-4, 0.0018276635, -0.025217136, 0.037127297, 0.0020467022, 0.027083363, 0.04393207, -0.09163813, -0.014773985, 0.019590687, 0.03746861, 0.05355276, 0.015302327, -0.056031857, 0.077545576, 0.019573858, 0.032816, 0.051690962, -0.07799171, -0.015421956, 0.035718523, -0.076702215, -0.020917052, -0.06810442, 0.00209874, -0.091363475, 0.07136104, 0.15810308, -0.0066730594, -0.0012587213, -0.06464794, 0.019459331, -0.04690046, 0.025799528, 0.0137103675, -0.053258784, -0.026784305, 0.06018044, 0.038596388, 0.061738253, -0.05322117, -0.10123451, 0.010938227, -0.06533964, 0.018773915, -0.057320554, 0.037652403, -0.0020998106, -0.040865876, -0.023697007, -0.02229583, 0.14273272, -0.0021852078, -0.010247714, -0.010886225, -2.163347E-33, -0.0019887935, -0.04658865, 0.06778884, -0.050473724, 0.012179547, 0.03189011, 0.024910972, -0.023081148, 0.005576965, -0.062806286, 0.02463773, 0.021953192, 0.019858396, -0.026508829, 0.07091947, -0.03475518, 0.013254987, 0.07807577, -0.015104437, -0.014010323, -0.016394444, -0.034263033, -0.029438986, 0.0062783216, 0.09192873, 0.014395727, -0.04826819, -0.06310329, 0.030082595, 0.060335964, -0.035525836, -0.019210255, 0.13722311, 0.011650414, -0.025777852, 0.047879558, -0.034088556, 0.016887333, 5.5594865E-4, -0.032090653, 0.112301916, -0.021276334, 0.051897764, -0.04174283, -0.069882244, -0.05209886, -0.013510875, 0.07280528, -0.027528381, 8.036902E-4, 0.068920836, 0.075812794, -0.011614407, -0.11180465, -0.101234615, -0.05068336, -0.059777215, -0.008318042, -0.03495864, 0.0143640805, -0.073223166, 0.05232535, -0.015237244, 0.066893056, -0.01687171, 0.00419431, 0.026944682, -0.055398524, 0.03503347, -0.02125077, -0.10205819, 0.0076973676, -0.049101807, 0.037173208, -0.016530704, 3.5218554E-4, -0.05153924, 0.042462893, -0.08441005, -0.008629542, -0.05185705, 0.026137767, -0.010335283, 0.017288204, 0.07953038, -0.006004999, 0.067192435, -0.040176384, -0.02369332, -0.03287697, -0.05108305, -0.08202445, -0.11295665, 0.08875428, 0.04570565, 8.455829E-35, 0.048689015, 0.007749114, 0.063158736, 0.001013862, -0.09695235, 0.023606412, -0.006290342, 0.03267311, -0.09156515, 0.055602048, -0.05078556, 0.0012825395, -0.0848496, -0.041716564, 0.030099295, -0.0016737913, -0.049840044, -0.056247845, -0.0153474575, 0.11222524, -0.0059483703, -0.05579404, -0.029848116, 0.0016613426, -0.04491148, 0.046980686, 0.0028564765, 0.008320842, 0.013569498, -0.022240778, -0.033440348, 0.004992154, -0.06942684, -0.15362318, -0.07114607, 0.0036061055, -0.025383294, -0.035691608, 0.01605192, -0.008260645, 0.0016610923, 0.0708099, -0.030619238, 0.047645602, -0.054889318, -0.094551265, 0.04000987, 0.13041452, -0.05763126, -0.02277902, -0.05587228, 0.03842295, -0.05002052, -0.036174446, -0.023267362, 0.041453008, -0.07026947, 0.009523977, -0.022798445, -0.0011159007, 0.024897018, -0.03263044, -0.10142198, 0.037371434, 0.08644441, -0.047301106, -0.029400254, -0.061682917, -0.11803087, -0.010528452, 0.08780984, 0.050029162, -0.012755704, -0.02532881, 0.019210795, 0.07881769, -0.037415136, -0.01525103, 0.012825792, 0.044247996, 0.07190492, 0.014891055, 0.05390874, 0.009154799, 0.05853525, 0.023270788, -0.063339435, 0.031965937, -0.061123163, -0.05340861, -0.02348483, -0.001859716, -0.03631427, 0.06266757, -0.032454584, -1.2968194E-8, -0.051838443, 0.003992061, 0.013850321, 0.04651716, 0.039252464, 0.06377298, -0.031984583, 0.013000329, 0.05000308, 0.008043318, 0.04926281, -0.0787264, -0.13963057, 0.041573446, -0.080932565, 0.012425371, 0.113427855, -0.006739183, -0.039879225, 0.047455925, -0.1427565, 0.024888374, -0.0057953494, -0.039866835, 0.066994905, -0.04864512, 0.0113134915, 0.07083669, 0.1204137, -0.017147439, 0.07444055, 0.022306466, 0.050016064, 0.107902415, 0.03212102, -0.026579512, -0.049095538, 0.038191266, -0.07642378, -0.059243236, 0.0857516, -0.0011516982, 0.010181804, -0.007178872, 0.016517213, 0.06849396, -0.0098535055, 0.01872266, 0.004257286, -0.0044438974, -0.038963076, -0.037256014, -0.004136929, 0.048959088, -0.04764037, -0.028560061, 0.044415016, 0.01065973, 0.056669142, 0.021713145, 0.027013293, -0.026512183, -0.05617165, -0.02196519]},\"embedded\":{\"text\":\"segment without metadata\",\"metadata\":{\"metadata\":{}}}},{\"id\":\"2\",\"embedding\":{\"vector\":[0.05837988, 0.09344276, -0.039457753, -0.08334625, 4.1723915E-4, -0.058542244, 0.074189395, 0.054527678, 0.00994289, -0.028339513, 0.009820215, -0.08387576, 0.035074454, 0.0028218343, 0.026988506, 0.016523479, -0.042796873, 0.06215258, -0.032226115, 0.025208335, 0.04459743, 0.07502659, 0.002371688, 0.03703751, -0.012270072, 0.08348183, -0.05567534, 0.005787425, 0.038656395, -0.016783383, 0.08176935, 0.10704945, 0.078571066, 0.05307949, 0.056630287, 0.043502484, 0.010093364, 0.047119897, 0.014350778, -0.019841172, -0.056514896, -0.02187556, -0.0180349, -0.020337401, -0.02152502, -0.014054545, 0.014667527, -0.022382345, -0.011042104, 0.06359725, -0.075823076, -0.029529905, -0.06173297, 0.092566036, 0.054986775, 0.06784886, 0.0057885693, -0.056477837, 0.008020879, 0.018132694, 4.923449E-4, -0.0043381206, -0.023514295, 0.0073864227, 0.032407656, 0.03176979, -0.058115438, -0.07274025, 0.014969316, -0.041859217, 0.037783533, -0.03433398, 0.017493041, -0.014173878, -0.019447312, 0.037223455, 0.0023598196, 0.025123706, 0.046292417, -0.15328601, 0.015340259, 0.031726554, 0.02753266, 0.043350082, 0.035163913, -0.085452214, 0.052260265, -0.006752804, 0.001629906, 0.07696195, -0.0230722, -0.020220032, 0.030653453, -0.068308435, -0.041278098, -0.044211537, 3.7859834E-5, -0.09415985, 0.10081561, 0.15496382, 0.014853234, -0.0015652339, -0.08011823, 0.022284854, -0.04223634, -0.0071037356, 0.007494289, -0.05179918, -0.04198075, 0.06347269, 0.04215269, 0.05668822, -0.094373934, -0.08719381, 0.012354686, -0.05357937, 0.0033235913, -0.0403174, 0.04964532, -0.020985432, -0.043271758, -0.015067258, -0.058684934, 0.12209202, 0.0035536818, -0.011580113, -0.041210275, -4.070006E-33, -0.007626669, -0.061368003, 0.07796328, -0.033604216, 0.008575677, -0.0051818453, -0.017927509, 0.0042236433, -0.033197504, -0.06703089, 0.00879987, 0.0036406594, -8.6677825E-4, -0.019542035, 0.058294937, -0.03722792, -0.0047161817, 0.10228197, -0.066011645, 0.02302273, -0.031706244, -0.053271342, -0.020773813, 0.00906788, 0.08532481, 0.022987785, -0.047276232, -0.05912104, 0.03255273, 0.047861602, -0.032209273, -0.013939997, 0.13194162, 0.011228575, -0.023902535, 0.049033094, -0.007912213, 0.021667516, 0.0036116408, -0.049954724, 0.087102786, 0.006853203, 0.059593026, -0.056072272, -0.0679339, -0.0030247932, -0.018270878, 0.0882874, -0.019353677, 0.030969758, 0.08018429, 0.032813434, -0.028545784, -0.0806185, -0.042335212, -0.027069038, -0.07248584, 0.01649164, -0.028100802, 0.03313925, -0.0502905, 0.055679258, 0.010046786, 0.058187425, -0.011710937, 0.03205539, 0.046763774, -0.0552734, 0.045685455, 0.033555202, -0.1197805, 0.018266637, -0.043609306, 0.06912254, -0.008946315, 0.016870946, -0.032237504, 0.01330031, -0.09712336, 4.9521454E-4, -0.10147847, 0.051576, -0.021106066, 0.011144064, 0.017778251, 0.0060047554, 0.05772322, -0.061269913, -0.029449057, -0.024617432, -0.025355957, -0.05719452, -0.06976631, 0.07709069, 0.019826017, 1.0224678E-33, 0.050604526, 0.017807676, 0.07282308, -0.0036491263, -0.07413099, -4.3155023E-4, -0.024511613, 0.06362351, -0.11114593, 0.07665981, -0.03692944, 0.007061206, -0.08720062, -0.055200804, 0.040345453, -0.0283173, -0.020413615, -0.07577535, 0.016257675, 0.113625415, -0.0056213485, -0.036638286, -0.0529244, 0.0028618309, -0.015472684, 0.034872197, 0.019705394, -0.015243864, 0.03271624, -0.024766102, -0.0535038, -0.031545445, -0.057887685, -0.10483516, -0.04064266, -0.009326459, -0.0077341287, -0.069033325, 0.017673176, -0.015703466, 0.01636901, 0.042970393, -0.024643304, 0.056382116, -0.05941725, -0.09357908, 0.049438637, 0.15838999, -0.056195747, -0.04784393, -0.0430123, 0.028257716, -0.032192707, -0.020910751, -0.025048988, 0.061618377, -0.07347489, -0.032990277, -0.050150815, -0.024723113, -0.018756708, -0.027919685, -0.057372812, 0.038768556, 0.07385629, -0.08152189, -0.031678732, -0.1057352, -0.13461548, 0.018559346, 0.0909795, 0.032460578, 0.011498575, 0.00392916, 0.028583221, 0.0458861, -0.021703769, -0.02280812, 0.009506054, 0.06315198, 0.04731373, 0.046541054, 0.05720671, 0.03835273, 0.044484988, 0.033921726, -0.023643738, 0.06596859, -0.06388584, -0.027793933, -0.044383846, 0.005738407, -0.037117574, 0.06635245, -0.058407348, -1.2430023E-8, -0.07637583, 0.00340603, -0.026113013, 0.021957425, 0.007894387, 0.08474347, -0.051829547, 8.5547206E-4, 0.045454033, 0.0042209104, 0.059463833, -0.04864894, -0.12216702, 0.022944214, -0.08345383, 0.007683397, 0.078910455, -0.0044849566, -0.031914327, 0.053438228, -0.10980423, 0.018961608, 0.017497955, -0.048067383, 0.043778565, -0.013376223, -0.003847124, 0.08962141, 0.09600368, -0.023600824, 0.06967719, 0.022684185, 0.031953696, 0.11791295, 0.045051366, -0.059446946, -0.055162594, 0.018231189, -0.05505258, -0.056634426, 0.11333107, -0.034801014, -0.01857788, -0.011792301, 0.037721515, 0.09515242, -0.01781604, 0.044649232, 0.0046286825, -0.0069176015, -0.049560755, -0.043363433, 0.004678153, 0.023735594, -0.040158838, -0.03780346, 0.010636716, -0.001430258, 0.03302453, -0.0076810997, 0.016793838, -0.0049853916, -0.0537008, 0.0038624643]},\"embedded\":{\"text\":\"segment with metadata\",\"metadata\":{\"metadata\":{\"key\":\"value\"}}}}]}";

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
