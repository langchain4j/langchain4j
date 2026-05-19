package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeminiCacheManagerTest {

    private static final String CACHE_KEY = "ai-assistant";
    private static final String MODEL = "gemini-3-flash-preview";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Test
    void cacheHitOnIdenticalInputs() {
        GeminiService service = stubbedService();
        AtomicInteger createdIds = new AtomicInteger();
        when(service.createCachedContent(anyString(), any()))
                .thenAnswer(inv -> echoCreate(inv.getArgument(1), createdIds.incrementAndGet()));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        GeminiContent content = textContent("system instruction");
        List<GeminiTool> tools = toolList("toolA", "toolB");

        String id1 = manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);
        String id2 = manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);

        assertThat(id1).isEqualTo(id2);
        verify(service, times(1)).createCachedContent(anyString(), any());
    }

    @Test
    void differentToolSetsCoexist() {
        GeminiService service = stubbedService();
        AtomicInteger createdIds = new AtomicInteger();
        when(service.createCachedContent(anyString(), any()))
                .thenAnswer(inv -> echoCreate(inv.getArgument(1), createdIds.incrementAndGet()));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        GeminiContent content = textContent("system");
        List<GeminiTool> toolsA = toolList("toolA");
        List<GeminiTool> toolsB = toolList("toolA", "toolB");

        String idA = manager.getOrCreateCached(CACHE_KEY, TTL, content, toolsA, null, MODEL);
        String idB = manager.getOrCreateCached(CACHE_KEY, TTL, content, toolsB, null, MODEL);
        String idAagain = manager.getOrCreateCached(CACHE_KEY, TTL, content, toolsA, null, MODEL);
        String idBagain = manager.getOrCreateCached(CACHE_KEY, TTL, content, toolsB, null, MODEL);

        assertThat(idA).isNotEqualTo(idB);
        assertThat(idAagain).isEqualTo(idA);
        assertThat(idBagain).isEqualTo(idB);
        verify(service, times(2)).createCachedContent(anyString(), any());
    }

    @Test
    void checksumIsOrderIndependent() {
        GeminiService service = stubbedService();
        AtomicInteger createdIds = new AtomicInteger();
        when(service.createCachedContent(anyString(), any()))
                .thenAnswer(inv -> echoCreate(inv.getArgument(1), createdIds.incrementAndGet()));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        GeminiContent content = textContent("system");
        List<GeminiTool> toolsAsc = toolList("alpha", "beta", "gamma");
        List<GeminiTool> toolsDesc = toolList("gamma", "beta", "alpha");

        String idAsc = manager.getOrCreateCached(CACHE_KEY, TTL, content, toolsAsc, null, MODEL);
        String idDesc = manager.getOrCreateCached(CACHE_KEY, TTL, content, toolsDesc, null, MODEL);

        assertThat(idAsc).isEqualTo(idDesc);
        verify(service, times(1)).createCachedContent(anyString(), any());
    }

    @Test
    void preexistingCacheIsReusedFromListing() {
        GeminiContent content = textContent("system");
        List<GeminiTool> tools = toolList("toolA");
        String displayName = effectiveKey(content, tools, null);

        GeminiCachedContent seed = cachedContent("cachedContents/seed-1", displayName, Instant.now().plus(TTL));
        GeminiService service = mock(GeminiService.class);
        when(service.listCachedContents(any())).thenReturn(listResponse(seed));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        String id = manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);

        assertThat(id).isEqualTo(seed.name());
        verify(service, never()).createCachedContent(anyString(), any());
    }

    @Test
    void expiredEntryRecreatesAndDoesNotDelete() {
        GeminiContent content = textContent("system");
        List<GeminiTool> tools = toolList("toolA");
        String displayName = effectiveKey(content, tools, null);

        GeminiCachedContent expired = cachedContent(
                "cachedContents/expired-1", displayName, Instant.now().minus(Duration.ofMinutes(1)));
        GeminiService service = mock(GeminiService.class);
        when(service.listCachedContents(any())).thenReturn(listResponse(expired));
        when(service.createCachedContent(anyString(), any()))
                .thenAnswer(inv -> echoCreate(inv.getArgument(1), 1));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);

        verify(service, times(1)).createCachedContent(anyString(), any());
        verify(service, never()).deleteCachedContent(anyString());
    }

    @Test
    void almostExpiredEntryExtendsTtl() {
        GeminiContent content = textContent("system");
        List<GeminiTool> tools = toolList("toolA");
        String displayName = effectiveKey(content, tools, null);

        GeminiCachedContent seed = cachedContent(
                "cachedContents/almost-1", displayName, Instant.now().plus(Duration.ofSeconds(30)));
        GeminiService service = mock(GeminiService.class);
        when(service.listCachedContents(any())).thenReturn(listResponse(seed));
        when(service.updateCachedContent(anyString(), any()))
                .thenAnswer(inv -> cachedContent(seed.name(), seed.displayName(), Instant.now().plus(TTL)));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);
        manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);

        verify(service, times(1)).updateCachedContent(anyString(), any());
        verify(service, never()).createCachedContent(anyString(), any());
    }

    @Test
    void concurrentCallsSameKeyCreateOnce() throws Exception {
        GeminiService service = stubbedService();
        CountDownLatch firstCreateEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCreate = new CountDownLatch(1);
        AtomicInteger createCount = new AtomicInteger();
        when(service.createCachedContent(anyString(), any())).thenAnswer(inv -> {
            int n = createCount.incrementAndGet();
            if (n == 1) {
                firstCreateEntered.countDown();
                releaseFirstCreate.await();
            }
            return echoCreate(inv.getArgument(1), n);
        });

        GeminiCacheManager manager = new GeminiCacheManager(service);
        GeminiContent content = textContent("system");
        List<GeminiTool> tools = toolList("toolA");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> f1 = pool.submit(() -> manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL));
            assertThat(firstCreateEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<String> f2 = pool.submit(() -> manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL));
            Thread.sleep(50);
            releaseFirstCreate.countDown();

            assertThat(f1.get(5, TimeUnit.SECONDS)).isEqualTo(f2.get(5, TimeUnit.SECONDS));
            assertThat(createCount.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void displayNameOnCreateEqualsEffectiveKey() {
        GeminiService service = stubbedService();
        when(service.createCachedContent(anyString(), any()))
                .thenAnswer(inv -> echoCreate(inv.getArgument(1), 1));

        GeminiCacheManager manager = new GeminiCacheManager(service);
        GeminiContent content = textContent("system");
        List<GeminiTool> tools = toolList("toolA");

        manager.getOrCreateCached(CACHE_KEY, TTL, content, tools, null, MODEL);

        ArgumentCaptor<GeminiCachedContent> captor = ArgumentCaptor.forClass(GeminiCachedContent.class);
        verify(service).createCachedContent(anyString(), captor.capture());
        String displayName = captor.getValue().displayName();
        assertThat(displayName).isEqualTo(effectiveKey(content, tools, null));
        assertThat(displayName).startsWith(CACHE_KEY + ":");
        assertThat(displayName.substring(CACHE_KEY.length() + 1)).matches("[a-f0-9]{64}");
    }

    // ----- helpers -----

    private static GeminiService stubbedService() {
        GeminiService service = mock(GeminiService.class);
        when(service.listCachedContents(any())).thenReturn(emptyListResponse());
        return service;
    }

    private static GoogleAiListCachedContentsResponse emptyListResponse() {
        GoogleAiListCachedContentsResponse response = new GoogleAiListCachedContentsResponse();
        response.setCachedContents(Collections.emptyList());
        return response;
    }

    private static GoogleAiListCachedContentsResponse listResponse(GeminiCachedContent... entries) {
        GoogleAiListCachedContentsResponse response = new GoogleAiListCachedContentsResponse();
        response.setCachedContents(List.of(entries));
        return response;
    }

    private static GeminiContent textContent(String text) {
        return new GeminiContent(List.of(GeminiPart.ofText(text)), "model");
    }

    private static List<GeminiTool> toolList(String... names) {
        List<GeminiFunctionDeclaration> declarations = Arrays.stream(names)
                .map(name -> GeminiFunctionDeclaration.builder().name(name).description(name + "-desc").build())
                .toList();
        return List.of(new GeminiTool(declarations, null, null, null, null));
    }

    private static String effectiveKey(GeminiContent content, List<GeminiTool> tools, GeminiToolConfig toolConfig) {
        return CACHE_KEY + ":" + GeminiCacheManager.getChecksum(content, tools, toolConfig);
    }

    private static GeminiCachedContent echoCreate(GeminiCachedContent input, int id) {
        return cachedContent("cachedContents/id-" + id, input.displayName(), Instant.now().plus(TTL));
    }

    private static GeminiCachedContent cachedContent(String name, String displayName, Instant expireAt) {
        return GeminiCachedContent.builder()
                .name(name)
                .displayName(displayName)
                .expireTime(expireAt.toString())
                .build();
    }

}
