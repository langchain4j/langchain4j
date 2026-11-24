package dev.langchain4j.data.media;

import static dev.langchain4j.data.media.TestHttpServer.chunked;
import static dev.langchain4j.data.media.TestHttpServer.delayed;
import static dev.langchain4j.data.media.TestHttpServer.headersOnly;
import static dev.langchain4j.data.media.TestHttpServer.ok;
import static dev.langchain4j.data.media.TestHttpServer.redirect;
import static dev.langchain4j.data.media.TestHttpServer.status;
import static dev.langchain4j.data.media.TestHttpServer.trickle;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.data.media.DefaultMediaFetcher.HostResolver;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;
import org.assertj.core.api.ThrowableAssertAlternative;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultMediaFetcherTest {

    private static final byte[] IMAGE = "fake-png-bytes".getBytes(UTF_8);

    private static final String ON_THE_MODEL_BUILDER =
            " on the model builder (on each model, token count estimator or cache that sees this URL)";

    /**
     * Lets 127.0.0.1 play a public server, so that the checks and the pinning of public hosts can be tested with a
     * local server. All other addresses are classified as usual.
     */
    private static final Predicate<InetAddress> LOCAL_SERVER_IS_PUBLIC =
            address -> address.getHostAddress().equals("127.0.0.1") || AddressClassifier.isPublic(address);

    private TestHttpServer server;
    private TestHttpServer other;

    @AfterEach
    void stopServers() {
        if (server != null) {
            server.close();
        }
        if (other != null) {
            other.close();
        }
    }

    private static DefaultMediaFetcher.Builder builder() {
        return DefaultMediaFetcher.builder();
    }

    private static DefaultMediaFetcher localhostAllowed() {
        return builder().allowNonPublicHosts("localhost").build();
    }

    /**
     * Builds a fetcher that resolves the given host names itself (to 127.0.0.1 unless given as {@code name=address,...})
     * and treats 127.0.0.1 as public. The JDK cannot resolve these names, so a request to them only reaches the local
     * server if it is sent to the address that the fetcher checked.
     */
    private static DefaultMediaFetcher withNames(DefaultMediaFetcher.Builder builder, String... names) {
        Map<String, String> addresses = new HashMap<>();
        for (String name : names) {
            String[] nameAndAddresses = name.split("=");
            addresses.put(nameAndAddresses[0], nameAndAddresses.length > 1 ? nameAndAddresses[1] : "127.0.0.1");
        }
        return new DefaultMediaFetcher(builder, resolver(addresses), LOCAL_SERVER_IS_PUBLIC);
    }

    private static HostResolver resolver(Map<String, String> addresses) {
        return host -> {
            String found = addresses.get(HostPatterns.normalize(host));
            if (found == null) {
                return InetAddress.getAllByName(host);
            }
            String[] literals = found.split(",");
            InetAddress[] result = new InetAddress[literals.length];
            for (int i = 0; i < literals.length; i++) {
                result[i] = InetAddress.getByName(literals[i]);
            }
            return result;
        };
    }

    private static ThrowableAssertAlternative<?> assertFailsWith(CompletableFuture<?> future) {
        return assertThat(future)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause();
    }

    private static boolean ipv6LoopbackAvailable() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getByName("::1"), 0));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // schemes and hosts

    @ParameterizedTest
    @ValueSource(
            strings = {
                "file:///etc/passwd",
                "FILE:///etc/passwd",
                "File:/etc/passwd",
                "cat.png",
                "/etc/passwd",
                "../../etc/passwd",
                "C:/Users/me/cat.png"
            })
    void should_reject_local_files_and_name_the_factories_that_read_them(String url) {
        DefaultMediaFetcher fetcher = builder().allowNonPublicHosts("localhost", "example.com").build();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(URI.create(url))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Only http and https URLs can be fetched, but got a URL ")
                .hasMessageContaining("Local files are not read")
                .hasMessageContaining("ImageContent.from(Path, String)")
                .hasMessageContaining("PdfFileContent.from(Path)")
                .hasMessageContaining("AudioContent.from(Path, String)")
                .hasMessageContaining("VideoContent.from(Path, String)")
                .hasMessageContaining("Image.builder().base64Data(...)")
                .hasMessageNotContaining("passwd")
                .hasMessageNotContaining("cat.png");
    }

    @Test
    void should_reject_data_urls_and_name_the_base64_factories() {
        DefaultMediaFetcher fetcher = builder().build();

        assertThatThrownBy(() -> fetcher.fetch(
                        MediaFetchRequest.from(URI.create("data:image/png;base64,iVBORw0KGgo="))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Only http and https URLs can be fetched, but got a URL with the scheme 'data'")
                .hasMessageContaining("ImageContent.from(String base64Data, String mimeType)")
                .hasMessageContaining("PdfFileContent.from(String base64Data, String mimeType)")
                .hasMessageContaining("AudioContent.from(String base64Data, String mimeType)")
                .hasMessageContaining("VideoContent.from(String base64Data, String mimeType)")
                .hasMessageNotContaining("iVBORw0KGgo");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jar:file:/tmp/app.jar!/application.properties",
                "jrt:/java.base/java/lang/Object.class",
                "ftp://example.com/cat.png",
                "gopher://example.com/",
                "mailto:someone@example.com"
            })
    void should_reject_other_schemes(String url) {
        DefaultMediaFetcher fetcher = builder().allowNonPublicHosts("localhost", "example.com").build();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(URI.create(url))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Only http and https URLs can be fetched, but got a URL with the scheme '"
                        + URI.create(url).getScheme() + "'");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http:///etc/passwd",
                "https:///cat.png",
                "http://0x7f.1/",
                "http://127.1/",
                "http://my_host.internal/",
                "http:cat.png"
            })
    void should_reject_urls_without_a_valid_host(String url) {
        assertThatThrownBy(() -> localhostAllowed().fetch(MediaFetchRequest.from(URI.create(url))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("The media URL has no valid host");
    }

    @Test
    void should_reject_loopback_without_connecting() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = builder().build();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("127.0.0.1", "/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Host '127.0.0.1' resolves to a non-public address. If you trust it, add it to"
                        + " allowNonPublicHosts: set"
                        + " mediaFetcher(DefaultMediaFetcher.builder().allowNonPublicHosts(\"127.0.0.1\").build())"
                        + ON_THE_MODEL_BUILDER);
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
        assertThat(server.hits()).isZero();
    }

    @Test
    void should_fetch_from_hosts_in_allowNonPublicHosts() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = localhostAllowed();

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("HTTP", "LOCALHOST", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("127.0.0.1", "/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
        assertThat(server.hits()).isEqualTo(1);
    }

    @Test
    void should_reject_ipv6_loopback() {
        assumeThat(ipv6LoopbackAvailable()).isTrue();
        server = TestHttpServer.http("::1").on("/", ok(IMAGE));
        URI url = server.url("[::1]", "/cat.png");

        assertThatThrownBy(() -> builder().build().fetch(MediaFetchRequest.from(url)))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
        assertThat(server.hits()).isZero();

        assertThat(builder().allowNonPublicHosts("[::1]").build().fetch(MediaFetchRequest.from(url)).bytes())
                .isEqualTo(IMAGE);
        assertThat(builder().allowNonPublicHosts("::1").build().fetch(MediaFetchRequest.from(url)).bytes())
                .isEqualTo(IMAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[::ffff:127.0.0.1]", "[::ffff:7f00:1]", "2130706433", "127.000.000.001"})
    void should_reject_other_forms_of_a_loopback_address_that_the_http_client_connects_to(String host) {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        URI url = server.url(host, "/cat.png");

        assertThatThrownBy(() -> builder().build().fetch(MediaFetchRequest.from(url)))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
        assertThat(server.hits()).isZero();

        // the JDK client really connects to 127.0.0.1 for this form of the address
        assertThat(builder().allowNonPublicHosts(host).build().fetch(MediaFetchRequest.from(url)).bytes())
                .isEqualTo(IMAGE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://0/",
                "http://0.0.0.0/",
                "http://[::]/",
                "http://[::127.0.0.1]/",
                "http://[64:ff9b::a9fe:a9fe]/",
                "http://169.254.169.254/latest/meta-data/",
                "http://[fd00:ec2::254]/",
                "http://100.100.100.200/",
                "http://10.0.0.1/",
                "http://192.168.0.1/",
                "http://172.16.0.1/"
            })
    void should_reject_non_public_addresses(String url) {
        assertThatThrownBy(() -> builder().build().fetch(MediaFetchRequest.from(URI.create(url))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void should_check_the_host_and_not_the_user_info() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));

        assertThatThrownBy(() -> builder()
                        .build()
                        .fetch(MediaFetchRequest.from(
                                URI.create("http://public.example.com@127.0.0.1:" + server.port() + "/"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("'127.0.0.1'");
        assertThat(server.hits()).isZero();
    }

    @Test
    void should_reject_a_host_when_any_of_its_addresses_is_not_public() {
        DefaultMediaFetcher fetcher = withNames(builder(), "multi.test=93.184.215.14,10.0.0.5");

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(URI.create("http://multi.test/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host 'multi.test' resolves to a non-public address");
    }

    @Test
    void should_fail_when_the_host_is_unknown() {
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder(),
                host -> {
                    throw new UnknownHostException(host);
                },
                AddressClassifier::isPublic);

        assertThatThrownBy(() ->
                        fetcher.fetch(MediaFetchRequest.from(URI.create("https://unknown.example.com/cat.png"))))
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessage("Unknown host 'unknown.example.com'")
                .hasCauseExactlyInstanceOf(UnknownHostException.class);
    }

    @Test
    void should_fail_with_a_LangChain4jException_when_the_http_client_cannot_send_the_request() {
        assertThatThrownBy(() -> localhostAllowed()
                        .fetch(MediaFetchRequest.from(URI.create("http://localhost:99999/cat.png"))))
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessage("Failed to fetch media from host 'localhost'")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    // allowedHosts and allowNonPublicHosts

    @Test
    void should_fetch_only_from_allowed_hosts() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(
                builder().allowedHosts("Media.Test", "*.CDN.test"),
                "media.test",
                "a.cdn.test",
                "b.a.cdn.test",
                "cdn.test",
                "evil.test",
                "notmedia.test");

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/a"))).bytes()).isEqualTo(IMAGE);
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("MEDIA.TEST.", "/a"))).bytes()).isEqualTo(IMAGE);
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("a.cdn.test", "/a"))).bytes()).isEqualTo(IMAGE);
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("b.a.cdn.test", "/a"))).bytes()).isEqualTo(IMAGE);
        for (String host : List.of("cdn.test", "evil.test", "notmedia.test")) {
            assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url(host, "/a"))))
                    .isExactlyInstanceOf(InvalidRequestException.class)
                    .hasMessage(notInAllowedHosts(host));
        }
        assertThat(server.hits()).isEqualTo(4);
    }

    private static String notInAllowedHosts(String host) {
        return "Host '" + host + "' is not in allowedHosts of the DefaultMediaFetcher. To allow it, add it to"
                + " allowedHosts: set mediaFetcher(DefaultMediaFetcher.builder().allowedHosts(..., \"" + host
                + "\").build())" + ON_THE_MODEL_BUILDER;
    }

    @Test
    void should_match_host_names_with_non_ascii_characters_in_their_ascii_form() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(builder().allowedHosts("b\u00fccher.test"), "xn--bcher-kva.test");

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("xn--bcher-kva.test", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
    }

    @Test
    void should_check_the_addresses_of_allowed_hosts_too() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = builder().allowedHosts("localhost").build();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/a"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
        assertThat(server.hits()).isZero();
    }

    @Test
    void should_apply_allowedHosts_to_redirects() {
        server = TestHttpServer.http("127.0.0.1");
        server.on("/start", redirect(server.url("evil.test", "/secret"))).on("/secret", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(builder().allowedHosts("media.test"), "media.test", "evil.test");

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/start"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage(notInAllowedHosts("evil.test"));
        assertThat(server.hits()).isEqualTo(1);
    }

    @Test
    void should_apply_allowNonPublicHosts_only_to_the_listed_hosts_also_after_a_redirect() {
        server = TestHttpServer.http("127.0.0.1");
        server.on("/start", redirect(server.url("127.0.0.1", "/secret"))).on("/secret", ok(IMAGE));

        assertThatThrownBy(() -> localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/start"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host '127.0.0.1' resolves to a non-public address");
        assertThat(server.hits()).isEqualTo(1);
    }

    @Test
    void should_require_a_non_public_host_to_be_in_allowedHosts_as_well() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = builder()
                .allowedHosts("images.example.com")
                .allowNonPublicHosts("localhost")
                .build();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/a"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("is not in allowedHosts");
        assertThat(server.hits()).isZero();
    }

    // redirects and pinning

    @Test
    void should_reject_a_redirect_to_a_non_public_address_before_connecting() {
        server = TestHttpServer.http("127.0.0.1");
        server.on("/a.png", redirect(server.url("internal.test", "/latest/meta-data")))
                .on("/b.png", redirect("http://169.254.169.254/latest/meta-data/"))
                .on("/latest", ok("secret".getBytes(UTF_8)));
        DefaultMediaFetcher fetcher = withNames(builder(), "media.test", "internal.test=10.0.0.5");

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/a.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host 'internal.test' resolves to a non-public address");
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/b.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host '169.254.169.254' resolves to a non-public address");
        assertThat(server.requests)
                .extracting(request -> request.requestUri().getPath())
                .containsExactly("/a.png", "/b.png");
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void should_follow_redirects(int status) {
        server = TestHttpServer.http("127.0.0.1")
                .on("/start", redirect(status, "/cat.png"))
                .on("/cat.png", ok(IMAGE));

        assertThat(localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/start"))).bytes())
                .isEqualTo(IMAGE);
        assertThat(server.requests)
                .extracting(TestHttpServer.Request::method, request -> request.requestUri().getPath())
                .containsExactly(tuple("GET", "/start"), tuple("GET", "/cat.png"));
    }

    @Test
    void should_keep_the_path_for_a_redirect_that_changes_only_the_query() {
        server = TestHttpServer.http("127.0.0.1").on("/", exchange -> {
            URI requestUri = exchange.getRequestURI();
            if (!requestUri.getPath().equals("/dir/cat.png")) {
                status(404).handle(exchange);
            } else if ("size=large".equals(requestUri.getQuery())) {
                redirect("?size=small").handle(exchange);
            } else {
                ok(IMAGE).handle(exchange);
            }
        });

        assertThat(localhostAllowed()
                        .fetch(MediaFetchRequest.from(server.url("localhost", "/dir/cat.png?size=large")))
                        .bytes())
                .isEqualTo(IMAGE);
        assertThat(server.requests)
                .extracting(request -> request.requestUri().toString())
                .containsExactly("/dir/cat.png?size=large", "/dir/cat.png?size=small");
    }

    @Test
    void should_follow_relative_absolute_path_and_scheme_relative_redirects() {
        server = TestHttpServer.http("127.0.0.1");
        server.on("/dir/start", redirect("next"))
                .on("/dir/next", redirect("/absolute"))
                .on("/absolute", redirect("//localhost:" + server.port() + "/final"))
                .on("/final", ok(IMAGE));

        assertThat(localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/dir/start"))).bytes())
                .isEqualTo(IMAGE);
        assertThat(server.requests)
                .extracting(request -> request.requestUri().getPath())
                .containsExactly("/dir/start", "/dir/next", "/absolute", "/final");
    }

    @Test
    void should_follow_a_relative_redirect_from_a_url_without_a_path() {
        server = TestHttpServer.http("127.0.0.1");
        server.on("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/")) {
                redirect("cat.png").handle(exchange);
            } else {
                ok(IMAGE).handle(exchange);
            }
        });

        assertThat(localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", ""))).bytes())
                .isEqualTo(IMAGE);
        assertThat(server.requests.get(1).requestUri().getPath()).isEqualTo("/cat.png");
    }

    @Test
    void should_send_plain_http_requests_to_the_checked_address() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(builder(), "pinned.test");

        // the JDK cannot resolve pinned.test, so the request reaches the server only through the checked address
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("pinned.test", "/cat.png?size=1"))).bytes())
                .isEqualTo(IMAGE);

        TestHttpServer.Request request = server.requests.get(0);
        assertThat(request.requestUri()).isEqualTo(server.url("pinned.test", "/cat.png?size=1"));
        assertThat(request.headers().getFirst("Host")).isEqualTo("pinned.test:" + server.port());
    }

    @Test
    void should_send_plain_http_requests_without_user_info_and_fragment() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(builder(), "pinned.test");
        URI url = URI.create("http://user:secret@pinned.test:" + server.port() + "/cat.png?size=1#part");

        assertThat(fetcher.fetch(MediaFetchRequest.from(url)).bytes()).isEqualTo(IMAGE);

        TestHttpServer.Request request = server.requests.get(0);
        assertThat(request.requestUri()).isEqualTo(server.url("pinned.test", "/cat.png?size=1"));
        assertThat(request.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void should_check_the_address_again_when_connecting() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        AtomicInteger lookups = new AtomicInteger();
        HostResolver changingAnswers = host -> lookups.incrementAndGet() == 1
                ? new InetAddress[] {InetAddress.getByName("127.0.0.1")}
                : new InetAddress[] {InetAddress.getByName("10.0.0.5")};
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(builder(), changingAnswers, LOCAL_SERVER_IS_PUBLIC);

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("rebind.test", "/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host 'rebind.test' resolves to a non-public address");
        assertThat(lookups).hasValue(2);
        assertThat(server.hits()).isZero();
    }

    @Test
    void should_send_plain_http_requests_to_a_checked_ipv6_address() {
        assumeThat(ipv6LoopbackAvailable()).isTrue();
        server = TestHttpServer.http("::1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder(), resolver(Map.of("v6.test", "::1")), address -> address.isLoopbackAddress());

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("v6.test", "/cat.png"))).bytes()).isEqualTo(IMAGE);
        assertThat(server.requests.get(0).requestUri()).isEqualTo(server.url("v6.test", "/cat.png"));
    }

    @Test
    void should_stop_after_maxRedirects() {
        server = TestHttpServer.http("127.0.0.1").on("/loop", redirect("/loop"));
        URI url = server.url("localhost", "/loop");

        assertThatThrownBy(() -> localhostAllowed().fetch(MediaFetchRequest.from(url)))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Host 'localhost' answered with a redirect (HTTP status 302), but maxRedirects (5) is"
                        + " reached. To follow more redirects, set"
                        + " mediaFetcher(DefaultMediaFetcher.builder().maxRedirects(...).build())"
                        + ON_THE_MODEL_BUILDER);
        assertThat(server.hits()).isEqualTo(6);

        server.requests.clear();
        assertThatThrownBy(() -> builder()
                        .allowNonPublicHosts("localhost")
                        .maxRedirects(0)
                        .build()
                        .fetch(MediaFetchRequest.from(url)))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("maxRedirects (0)");
        assertThat(server.hits()).isEqualTo(1);

        server.requests.clear();
        assertThatThrownBy(() -> builder()
                        .allowNonPublicHosts("localhost")
                        .maxRedirects(2)
                        .build()
                        .fetch(MediaFetchRequest.from(url)))
                .isExactlyInstanceOf(InvalidRequestException.class);
        assertThat(server.hits()).isEqualTo(3);
    }

    @Test
    void should_reject_invalid_redirects() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/no-location", status(302))
                .on("/invalid", redirect("http://exa mple.com/cat.png"))
                .on("/file", redirect("file:///etc/passwd"));
        DefaultMediaFetcher fetcher = localhostAllowed();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/no-location"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Host 'localhost' answered with a redirect (HTTP status 302) without a Location header");
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/invalid"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Host 'localhost' answered with a redirect to an invalid URL (")
                .hasMessageNotContaining("exa mple.com")
                .hasNoCause();
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/file"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only http and https URLs can be fetched");
    }

    @Test
    void should_fetch_https_and_reject_a_redirect_from_https_to_http() {
        other = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        server = TestHttpServer.https("127.0.0.1")
                .on("/cat.png", ok(IMAGE))
                .on("/downgrade", redirect(other.url("localhost", "/cat.png")));
        DefaultMediaFetcher fetcher = builder()
                .allowNonPublicHosts("localhost")
                .httpClientBuilder(HttpClient.newBuilder().sslContext(TestHttpServer.clientSslContext()))
                .build();

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("https", "localhost", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("https", "localhost", "/downgrade"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Host 'localhost' answered with a redirect from https to http, which is not allowed");
        assertThat(other.hits()).isZero();
    }

    @Test
    void should_verify_certificates() {
        server = TestHttpServer.https("127.0.0.1").on("/", ok(IMAGE));

        assertThatThrownBy(() -> localhostAllowed()
                        .fetch(MediaFetchRequest.from(server.url("https", "localhost", "/cat.png"))))
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessage("Failed to fetch media from host 'localhost'");
        assertThat(server.hits()).isZero();
    }

    // size, timeout and HTTP details

    @Test
    void should_reject_content_larger_than_maxSizeInBytes() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/exact", ok(new byte[1024]))
                .on("/declared", ok(new byte[1025]))
                .on("/chunked", chunked(10L * 1024 * 1024));
        DefaultMediaFetcher fetcher =
                builder().allowNonPublicHosts("localhost").maxSizeInBytes(1024).build();

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/exact"))).bytes()).hasSize(1024);
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/declared"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage(tooLarge("localhost", 1024));
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/chunked"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage(tooLarge("localhost", 1024));
    }

    private static String tooLarge(String host, int maxSizeInBytes) {
        return "The media from host '" + host + "' is larger than maxSizeInBytes (" + maxSizeInBytes
                + " bytes). To allow larger media, set"
                + " mediaFetcher(DefaultMediaFetcher.builder().maxSizeInBytes(...).build())" + ON_THE_MODEL_BUILDER;
    }

    @Test
    void should_limit_the_size_to_50_MiB_by_default_without_reading_the_body() {
        server = TestHttpServer.http("127.0.0.1").on("/", headersOnly(52_428_801));
        long start = System.nanoTime();

        assertThatThrownBy(() -> localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/big"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage(tooLarge("localhost", 50 * 1024 * 1024));

        // the server waits 10 seconds after the headers and never sends the body
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
    }

    @Test
    void should_stop_reading_an_endless_body_at_maxSizeInBytes() {
        server = TestHttpServer.http("127.0.0.1").on("/endless", chunked(Long.MAX_VALUE));
        DefaultMediaFetcher fetcher = builder()
                .allowNonPublicHosts("localhost")
                .maxSizeInBytes(1024 * 1024)
                .build();
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/endless"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("maxSizeInBytes");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        awaitClientAborted(server);
    }

    private static void awaitClientAborted(TestHttpServer server) {
        await().atMost(Duration.ofSeconds(10)).untilTrue(server.clientAborted);
    }

    @Test
    void should_time_out_when_the_headers_are_slow() {
        server = TestHttpServer.http("127.0.0.1").on("/", delayed(30_000, ok(IMAGE)));
        DefaultMediaFetcher fetcher = builder()
                .allowNonPublicHosts("localhost")
                .timeout(Duration.ofMillis(500))
                .build();
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .isExactlyInstanceOf(TimeoutException.class)
                .hasMessage("Fetching media from host 'localhost' did not complete within the timeout (PT0.5S)");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void should_time_out_when_the_body_is_slow() {
        server = TestHttpServer.http("127.0.0.1").on("/", trickle(100, 300));
        DefaultMediaFetcher fetcher = builder()
                .allowNonPublicHosts("localhost")
                .timeout(Duration.ofSeconds(1))
                .build();
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .isExactlyInstanceOf(TimeoutException.class)
                .hasMessageContaining("did not complete within the timeout (PT1S)");

        // the whole body takes 30 seconds
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void should_apply_one_timeout_to_the_whole_redirect_chain() {
        server = TestHttpServer.http("127.0.0.1").on("/hop", delayed(400, redirect("/hop")));
        DefaultMediaFetcher fetcher = builder()
                .allowNonPublicHosts("localhost")
                .maxRedirects(75)
                .timeout(Duration.ofSeconds(1))
                .build();
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/hop"))))
                .isExactlyInstanceOf(TimeoutException.class)
                .hasMessageContaining("did not complete within the timeout");

        // following all redirects takes 30 seconds
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
        assertThat(server.hits()).isLessThanOrEqualTo(4);
    }

    @Test
    void fetchAsync_should_time_out_when_the_host_lookup_is_slow() {
        CountDownLatch lookupMayAnswer = new CountDownLatch(1);
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder().timeout(Duration.ofMillis(300)),
                host -> {
                    try {
                        lookupMayAnswer.await(30, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    throw new UnknownHostException(host);
                },
                AddressClassifier::isPublic);
        long start = System.nanoTime();

        try {
            assertFailsWith(fetcher.fetchAsync(
                            MediaFetchRequest.from(URI.create("https://slow-dns.example.com/cat.png"))))
                    .isExactlyInstanceOf(TimeoutException.class)
                    .withMessageContaining("did not complete within the timeout (PT0.3S)");

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
        } finally {
            lookupMayAnswer.countDown();
        }
    }

    @Test
    void fetch_should_look_up_the_host_on_the_calling_thread_and_count_it_towards_the_timeout() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        AtomicReference<Thread> lookupThread = new AtomicReference<>();
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder().timeout(Duration.ofMillis(300)),
                host -> {
                    lookupThread.set(Thread.currentThread());
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
                },
                LOCAL_SERVER_IS_PUBLIC);

        // the lookup is not cut short, but there is no time left for the request
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("slow-dns.test", "/cat.png"))))
                .isExactlyInstanceOf(TimeoutException.class)
                .hasMessage("Fetching media from host 'slow-dns.test' did not complete within the timeout (PT0.3S)");
        assertThat(lookupThread.get()).isSameAs(Thread.currentThread());
        assertThat(server.hits()).isZero();
    }

    @Test
    void should_report_a_connect_timeout_with_connectTimeout() throws Exception {
        List<Socket> backlog = new ArrayList<>();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            assumeThat(fillBacklog(serverSocket, backlog))
                    .as("the operating system leaves connections to a full backlog unanswered")
                    .isTrue();
            DefaultMediaFetcher fetcher = builder()
                    .allowNonPublicHosts("127.0.0.1")
                    .connectTimeout(Duration.ofMillis(300))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(
                            URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/cat.png"))))
                    .isExactlyInstanceOf(TimeoutException.class)
                    .hasMessage("Could not connect to host '127.0.0.1' within connectTimeout (PT0.3S)")
                    .hasCauseInstanceOf(HttpConnectTimeoutException.class);
        } finally {
            for (Socket socket : backlog) {
                socket.close();
            }
        }
    }

    /**
     * Opens connections that the server never accepts until its backlog is full, so that the next connection is not
     * answered at all.
     */
    private static boolean fillBacklog(ServerSocket serverSocket, List<Socket> sockets) throws IOException {
        for (int i = 0; i < 10; i++) {
            Socket socket = new Socket();
            try {
                socket.connect(serverSocket.getLocalSocketAddress(), 500);
                sockets.add(socket);
            } catch (SocketTimeoutException e) {
                socket.close();
                return true;
            } catch (IOException e) {
                socket.close();
                return false;
            }
        }
        return false;
    }

    @Test
    void should_send_a_user_agent_and_no_cookies_and_not_ask_for_compressed_content() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/set", exchange -> {
                    exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Path=/");
                    redirect("/get").handle(exchange);
                })
                .on("/get", ok(IMAGE));
        DefaultMediaFetcher fetcher = localhostAllowed();

        fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/set")));
        fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/get")));

        assertThat(server.requests).hasSize(3).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.headers().get("User-Agent")).containsExactly("LangChain4j");
            assertThat(request.headers()).doesNotContainKeys("Cookie", "Accept-encoding", "Authorization");
        });
    }

    @Test
    void should_ignore_the_jvm_wide_cookie_handler_and_authenticator() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/set", exchange -> {
                    exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Path=/");
                    ok(IMAGE).handle(exchange);
                })
                .on("/get", ok(IMAGE))
                .on("/auth", status(401, "WWW-Authenticate", "Basic realm=\"media\""));
        CookieHandler cookieHandler = CookieHandler.getDefault();
        Authenticator authenticator = Authenticator.getDefault();
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("user", "secret".toCharArray());
            }
        });
        try {
            DefaultMediaFetcher fetcher = localhostAllowed();

            fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/set")));
            fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/get")));
            assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/auth"))))
                    .isExactlyInstanceOf(InvalidRequestException.class)
                    .hasMessage("Host 'localhost' answered with HTTP status 401");

            assertThat(server.requests)
                    .hasSize(3)
                    .allSatisfy(request -> assertThat(request.headers()).doesNotContainKeys("Cookie", "Authorization"));
        } finally {
            CookieHandler.setDefault(cookieHandler);
            Authenticator.setDefault(authenticator);
        }
    }

    @Test
    void should_ignore_the_jvm_wide_proxy_settings() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        ProxySelector proxySelector = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", other.port())));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException e) {}
        });
        try {
            assertThat(localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))).bytes())
                    .isEqualTo(IMAGE);
            assertThat(withNames(builder(), "media.test")
                            .fetch(MediaFetchRequest.from(server.url("media.test", "/cat.png")))
                            .bytes())
                    .isEqualTo(IMAGE);
            assertThat(other.hits()).isZero();
        } finally {
            ProxySelector.setDefault(proxySelector);
        }
    }

    @Test
    void should_return_compressed_content_as_it_is() throws Exception {
        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzipped)) {
            gzip.write(IMAGE);
        }
        server = TestHttpServer.http("127.0.0.1").on("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            ok(gzipped.toByteArray()).handle(exchange);
        });

        assertThat(localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))).bytes())
                .isEqualTo(gzipped.toByteArray());
    }

    @ParameterizedTest
    @ValueSource(ints = {300, 304, 400, 401, 403, 404, 410, 451})
    void should_reject_responses_that_are_not_successful(int status) {
        server = TestHttpServer.http("127.0.0.1").on("/", TestHttpServer.status(status));

        assertThatThrownBy(() -> localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Host 'localhost' answered with HTTP status " + status);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504, 599})
    void should_report_answers_that_may_succeed_when_retried_with_a_retriable_exception(int status) {
        server = TestHttpServer.http("127.0.0.1").on("/", TestHttpServer.status(status));
        Class<? extends LangChain4jException> expected = status == 408
                ? TimeoutException.class
                : status == 429 ? RateLimitException.class : InternalServerException.class;
        DefaultMediaFetcher fetcher = localhostAllowed();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .isExactlyInstanceOf(expected)
                .hasMessage("Host 'localhost' answered with HTTP status " + status);
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .isExactlyInstanceOf(expected)
                .withMessage("Host 'localhost' answered with HTTP status " + status);
    }

    @Test
    void should_return_the_mime_type_of_the_response() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/png", ok(IMAGE, "Image/PNG; charset=binary"))
                .on("/blank", ok(IMAGE, " "))
                .on("/none", ok(IMAGE));
        DefaultMediaFetcher fetcher = localhostAllowed();

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/png"))))
                .isEqualTo(FetchedMedia.from(IMAGE, "image/png"));
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/blank"))).mimeType()).isNull();
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/none"))).mimeType()).isNull();
    }

    @Test
    void should_fetch_empty_content() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(new byte[0]));

        assertThat(localhostAllowed().fetch(MediaFetchRequest.from(server.url("localhost", "/empty"))).bytes())
                .isEmpty();
    }

    // custom HttpClient.Builder

    @Test
    void should_use_the_proxy_of_a_custom_http_client_builder() {
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        ProxySelector proxy = ProxySelector.of(new InetSocketAddress("127.0.0.1", other.port()));
        DefaultMediaFetcher fetcher =
                withNames(builder().httpClientBuilder(HttpClient.newBuilder().proxy(proxy)), "media.test");

        assertThat(fetcher.fetch(MediaFetchRequest.from(URI.create("http://media.test/cat.png"))).bytes())
                .isEqualTo("from-proxy".getBytes(UTF_8));
        assertThat(other.requests.get(0).requestUri()).isEqualTo(URI.create("http://media.test/cat.png"));
    }

    @Test
    void should_pin_plain_http_requests_that_the_proxy_selector_of_a_custom_http_client_builder_sends_directly() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        ProxySelector direct = selector(List.of(Proxy.NO_PROXY));
        ProxySelector empty = selector(List.of());
        ProxySelector socks = selector(List.of(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1))));

        for (ProxySelector proxySelector : List.of(HttpClient.Builder.NO_PROXY, direct, empty, socks)) {
            DefaultMediaFetcher fetcher = withNames(
                    builder().httpClientBuilder(HttpClient.newBuilder().proxy(proxySelector)), "pinned.test");

            // the JDK cannot resolve pinned.test, so the request reaches the server only through the checked address
            assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("pinned.test", "/cat.png")))
                            .bytes())
                    .isEqualTo(IMAGE);
        }
        assertThat(server.hits()).isEqualTo(4);
    }

    @Test
    void should_pin_plain_http_requests_with_the_default_proxy_selector_without_proxy_settings() {
        assumeThat(System.getProperty("http.proxyHost")).isNull();
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(
                builder().httpClientBuilder(HttpClient.newBuilder().proxy(ProxySelector.getDefault())), "pinned.test");

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("pinned.test", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
    }

    private static ProxySelector selector(List<Proxy> proxies) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return proxies;
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException e) {}
        };
    }

    @Test
    void should_set_connectTimeout_on_a_custom_http_client_builder_only_when_it_is_set() {
        Duration ownTimeout = Duration.ofSeconds(42);

        assertThat(builder().build().httpClient().connectTimeout()).contains(Duration.ofSeconds(10));
        assertThat(builder().connectTimeout(Duration.ofSeconds(7)).build().httpClient().connectTimeout())
                .contains(Duration.ofSeconds(7));
        assertThat(builder()
                        .httpClientBuilder(HttpClient.newBuilder().connectTimeout(ownTimeout))
                        .build()
                        .httpClient()
                        .connectTimeout())
                .contains(ownTimeout);
        assertThat(builder().httpClientBuilder(HttpClient.newBuilder()).build().httpClient().connectTimeout())
                .isEmpty();
        assertThat(builder()
                        .connectTimeout(Duration.ofSeconds(7))
                        .httpClientBuilder(HttpClient.newBuilder().connectTimeout(ownTimeout))
                        .build()
                        .httpClient()
                        .connectTimeout())
                .contains(Duration.ofSeconds(7));
    }

    @Test
    void should_still_check_addresses_with_the_proxy_of_a_custom_http_client_builder() {
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        DefaultMediaFetcher fetcher = builder()
                .httpClientBuilder(HttpClient.newBuilder()
                        .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", other.port()))))
                .build();

        assertThatThrownBy(() ->
                        fetcher.fetch(MediaFetchRequest.from(URI.create("http://169.254.169.254/latest/meta-data/"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public");
        assertThat(other.hits()).isZero();
    }

    private static InetAddress[] unknownHost(String host) throws UnknownHostException {
        throw new UnknownHostException(host);
    }

    private static HttpClient.Builder withProxy(int port) {
        return HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", port)));
    }

    @Test
    void should_let_the_proxy_resolve_a_host_name_that_cannot_be_resolved_here() {
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder().httpClientBuilder(withProxy(other.port())),
                DefaultMediaFetcherTest::unknownHost,
                AddressClassifier::isPublic);

        assertThat(fetcher.fetch(MediaFetchRequest.from(URI.create("http://only-the-proxy-knows.test/cat.png")))
                        .bytes())
                .isEqualTo("from-proxy".getBytes(UTF_8));
        assertThat(fetcher.fetchAsync(MediaFetchRequest.from(URI.create("http://only-the-proxy-knows.test/dog.png"))))
                .succeedsWithin(10, SECONDS)
                .isEqualTo(FetchedMedia.from("from-proxy".getBytes(UTF_8)));
        // the request is sent to the proxy in absolute form, so the proxy resolves the host name
        assertThat(other.requests)
                .extracting(TestHttpServer.Request::requestUri)
                .containsExactly(
                        URI.create("http://only-the-proxy-knows.test/cat.png"),
                        URI.create("http://only-the-proxy-knows.test/dog.png"));
    }

    @Test
    void should_let_the_proxy_resolve_a_host_name_that_cannot_be_resolved_here_for_https() throws Exception {
        try (ServerSocket proxy = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            CompletableFuture<String> requestLine = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = proxy.accept()) {
                    String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8)).readLine();
                    OutputStream out = socket.getOutputStream();
                    out.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".getBytes(UTF_8));
                    out.flush();
                    return line;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                    builder().httpClientBuilder(withProxy(proxy.getLocalPort())),
                    DefaultMediaFetcherTest::unknownHost,
                    AddressClassifier::isPublic);

            // the fake proxy refuses to open the tunnel after recording the request for it
            assertThatThrownBy(() -> fetcher.fetch(
                            MediaFetchRequest.from(URI.create("https://only-the-proxy-knows.test/cat.png"))))
                    .isExactlyInstanceOf(LangChain4jException.class)
                    .hasMessage("Failed to fetch media from host 'only-the-proxy-knows.test'");
            assertThat(requestLine)
                    .succeedsWithin(10, SECONDS)
                    .isEqualTo("CONNECT only-the-proxy-knows.test:443 HTTP/1.1");
        }
    }

    @Test
    void should_fail_for_a_host_name_that_cannot_be_resolved_here_when_the_request_does_not_go_through_a_proxy() {
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        ProxySelector httpsOnly = new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return "https".equals(uri.getScheme())
                        ? List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", other.port())))
                        : List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException e) {}
        };
        ProxySelector socks = selector(List.of(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1))));

        for (DefaultMediaFetcher.Builder builder : List.of(
                builder(),
                builder().httpClientBuilder(HttpClient.newBuilder()),
                builder().httpClientBuilder(HttpClient.newBuilder().proxy(httpsOnly)),
                builder().httpClientBuilder(HttpClient.newBuilder().proxy(socks)))) {
            DefaultMediaFetcher fetcher =
                    new DefaultMediaFetcher(builder, DefaultMediaFetcherTest::unknownHost, AddressClassifier::isPublic);

            assertThatThrownBy(() -> fetcher.fetch(
                            MediaFetchRequest.from(URI.create("http://only-the-proxy-knows.test/cat.png"))))
                    .isExactlyInstanceOf(LangChain4jException.class)
                    .hasMessage("Unknown host 'only-the-proxy-knows.test'")
                    .hasCauseExactlyInstanceOf(UnknownHostException.class);
        }
        assertThat(other.hits()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x7f000001", "0X7F000001", "017700000001", "4294967297", "[fd00::1]"})
    void should_not_pass_ip_addresses_that_cannot_be_resolved_here_to_the_proxy(String host) {
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder().httpClientBuilder(withProxy(other.port())),
                DefaultMediaFetcherTest::unknownHost,
                AddressClassifier::isPublic);

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(URI.create("http://" + host + "/cat.png"))))
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessage("Unknown host '" + host + "'");
        assertThat(other.hits()).isZero();
    }

    @Test
    void should_still_reject_a_host_name_that_resolves_here_to_a_non_public_address_with_a_proxy() {
        other = TestHttpServer.http("127.0.0.1").on("/", ok("from-proxy".getBytes(UTF_8)));
        DefaultMediaFetcher fetcher =
                withNames(builder().httpClientBuilder(withProxy(other.port())), "intranet.test=10.0.0.5");

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(URI.create("http://intranet.test/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host 'intranet.test' resolves to a non-public address");
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(URI.create("https://intranet.test/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .withMessageContaining("Host 'intranet.test' resolves to a non-public address");
        assertThat(other.hits()).isZero();
    }

    @Test
    void should_pin_and_never_let_the_http_client_follow_redirects_with_a_custom_http_client_builder() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/cat.png", ok(IMAGE))
                .on("/redirect", redirect("http://169.254.169.254/latest/meta-data/"));
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS);
        DefaultMediaFetcher fetcher = withNames(builder().httpClientBuilder(httpClientBuilder), "media.test");
        DefaultMediaFetcher secondFetcher = withNames(builder().httpClientBuilder(httpClientBuilder), "other.test");

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
        assertThat(secondFetcher.fetch(MediaFetchRequest.from(server.url("other.test", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/redirect"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Host '169.254.169.254' resolves to a non-public address");
        assertThat(fetcher.httpClient().followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(fetcher.httpClient().proxy()).isPresent();
        assertThat(secondFetcher.httpClient().proxy()).isNotEqualTo(fetcher.httpClient().proxy());
    }

    @Test
    void should_create_the_http_client_on_the_first_download() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        DefaultMediaFetcher fetcher = localhostAllowed();
        assertThat(fetcher.isHttpClientCreated()).isFalse();

        assertThatThrownBy(() -> fetcher.fetch(MediaFetchRequest.from(URI.create("file:///etc/passwd"))))
                .isExactlyInstanceOf(InvalidRequestException.class);
        assertThat(fetcher.isHttpClientCreated()).isFalse();

        fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/cat.png")));
        assertThat(fetcher.isHttpClientCreated()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(60)
    void should_let_the_http_client_and_its_selector_thread_of_a_discarded_fetcher_end(boolean customHttpClientBuilder) {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));

        DiscardedFetcher discarded = fetchOnceAndDiscard(customHttpClientBuilder);

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).until(() -> {
            System.gc();
            return discarded.httpClient().get() == null
                    && !discarded.selectorThread().isAlive();
        });
    }

    private record DiscardedFetcher(WeakReference<HttpClient> httpClient, Thread selectorThread) {}

    private DiscardedFetcher fetchOnceAndDiscard(boolean customHttpClientBuilder) {
        DefaultMediaFetcher.Builder builder =
                customHttpClientBuilder ? builder().httpClientBuilder(HttpClient.newBuilder()) : builder();
        // a pinned host, so that the request goes through the proxy selector of the fetcher
        DefaultMediaFetcher fetcher = withNames(builder, "pinned.test");
        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("pinned.test", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
        HttpClient httpClient = fetcher.httpClient();
        return new DiscardedFetcher(new WeakReference<>(httpClient), selectorThreadOf(httpClient));
    }

    /**
     * Finds the thread of the JDK client that serves its connections. The client ends it once the client is no longer
     * referenced. Its toString() ends with "(id)", and the thread is named "HttpClient-id-SelectorManager".
     */
    private static Thread selectorThreadOf(HttpClient httpClient) {
        String description = httpClient.toString();
        String id = description.substring(description.lastIndexOf('(') + 1, description.length() - 1);
        String name = "HttpClient-" + id + "-SelectorManager";
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No thread named " + name + " for " + description));
    }

    // fetch interrupted

    @Test
    void fetch_should_stop_the_download_and_keep_the_interrupt_flag_when_the_calling_thread_is_interrupted()
            throws Exception {
        server = TestHttpServer.http("127.0.0.1").on("/slow", trickle(50, 100_000));
        DefaultMediaFetcher fetcher = localhostAllowed();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptFlag = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                fetcher.fetch(MediaFetchRequest.from(server.url("localhost", "/slow")));
            } catch (Throwable e) {
                thrown.set(e);
                interruptFlag.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> server.hits() > 0);

        caller.interrupt();
        caller.join(10_000);

        assertThat(caller.isAlive()).isFalse();
        assertThat(thrown.get())
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessage("Interrupted while fetching media")
                .hasCauseExactlyInstanceOf(InterruptedException.class);
        assertThat(interruptFlag).isTrue();
        awaitClientAborted(server);
    }

    // fetchAsync

    @Test
    void fetchAsync_should_report_every_failure_through_the_future() {
        server = TestHttpServer.http("127.0.0.1")
                .on("/cat.png", ok(IMAGE))
                .on("/big", ok(new byte[2048]))
                .on("/missing", status(404))
                .on("/slow", delayed(5_000, ok(IMAGE)));
        DefaultMediaFetcher fetcher = builder()
                .allowNonPublicHosts("localhost")
                .maxSizeInBytes(1024)
                .timeout(Duration.ofMillis(500))
                .build();

        assertFailsWith(fetcher.fetchAsync(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .withMessage("request cannot be null");
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(URI.create("file:///etc/passwd"))))
                .isExactlyInstanceOf(InvalidRequestException.class);
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(server.url("127.0.0.1", "/cat.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .withMessageContaining("non-public");
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(server.url("localhost", "/big"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .withMessageContaining("maxSizeInBytes");
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(server.url("localhost", "/missing"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .withMessageContaining("404");
        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(server.url("localhost", "/slow"))))
                .isExactlyInstanceOf(TimeoutException.class)
                .withMessageContaining("timeout");
        assertThat(fetcher.fetchAsync(MediaFetchRequest.from(server.url("localhost", "/cat.png"))))
                .succeedsWithin(10, SECONDS)
                .isEqualTo(FetchedMedia.from(IMAGE));
    }

    @Test
    void fetchAsync_should_not_block_the_calling_thread() {
        server = TestHttpServer.http("127.0.0.1").on("/", ok(IMAGE));
        CountDownLatch lookupMayAnswer = new CountDownLatch(1);
        AtomicReference<Thread> lookupThread = new AtomicReference<>();
        DefaultMediaFetcher fetcher = new DefaultMediaFetcher(
                builder(),
                host -> {
                    lookupThread.compareAndSet(null, Thread.currentThread());
                    try {
                        lookupMayAnswer.await(30, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
                },
                LOCAL_SERVER_IS_PUBLIC);
        AtomicReference<Thread> callingThread = new AtomicReference<>();
        try {
            // the lookup cannot answer before the call returns, so a call that waited for it would not return
            CompletableFuture<CompletableFuture<FetchedMedia>> call = CompletableFuture.supplyAsync(() -> {
                callingThread.set(Thread.currentThread());
                return fetcher.fetchAsync(MediaFetchRequest.from(server.url("slow-dns.test", "/cat.png")));
            });
            CompletableFuture<FetchedMedia> media =
                    assertThat(call).succeedsWithin(10, SECONDS).actual();

            assertThat(media).isNotDone();
            lookupMayAnswer.countDown();
            assertThat(media).succeedsWithin(10, SECONDS).isEqualTo(FetchedMedia.from(IMAGE));
        } finally {
            lookupMayAnswer.countDown();
        }
        assertThat(lookupThread.get()).isNotSameAs(callingThread.get());
    }

    @Test
    void fetchAsync_should_reject_a_redirect_to_a_non_public_address() {
        server = TestHttpServer.http("127.0.0.1");
        server.on("/a.png", redirect(server.url("internal.test", "/secret"))).on("/secret", ok(IMAGE));
        DefaultMediaFetcher fetcher = withNames(builder(), "media.test", "internal.test=10.0.0.5");

        assertFailsWith(fetcher.fetchAsync(MediaFetchRequest.from(server.url("media.test", "/a.png"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .withMessageContaining("Host 'internal.test' resolves to a non-public address");
        assertThat(server.hits()).isEqualTo(1);
    }

    @Test
    void fetchAsync_should_stop_the_download_when_the_future_is_cancelled() {
        server = TestHttpServer.http("127.0.0.1").on("/slow", trickle(50, 100_000));
        CompletableFuture<FetchedMedia> media =
                localhostAllowed().fetchAsync(MediaFetchRequest.from(server.url("localhost", "/slow")));
        await().atMost(Duration.ofSeconds(10)).until(() -> server.hits() > 0);

        media.cancel(true);

        awaitClientAborted(server);
    }
}
