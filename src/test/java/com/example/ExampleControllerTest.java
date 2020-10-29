package com.example;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpRequest.BodyPublishers.ofInputStream;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class ExampleControllerTest {

    private final EmbeddedServer server;

    @Inject
    public ExampleControllerTest(EmbeddedServer server) {
        this.server = server;
    }

    @BeforeEach
    public void startServer() {
        server.start();
    }

    @Test
    public void test() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException, ExecutionException {
        final var httpClient = HttpClient
            .newBuilder()
            // .version(HTTP_1_1) // The test will pass if this is uncommented
            .sslContext(createSSLContextThatIgnoresInvalidCertificates())
            .build();
        final var untilUploadHasStarted = new CountDownLatch(1);
        final var untilDownloadIsFinished = new CountDownLatch(1);
        final var upload = HttpRequest
            .newBuilder()
            .uri(URI.create("https://localhost:8443/upload"))
            .PUT(ofInputStream(() -> new InputStream() {

                private final Random random = new Random();
                private int numberOfBytesLeftToWrite = 1024 * 1024;

                @Override
                public int read() throws IOException {
                    if (--numberOfBytesLeftToWrite < 0) {
                        try {
                            untilDownloadIsFinished.await();
                        } catch (InterruptedException exception) {
                            throw new IOException(exception);
                        }
                        return -1;
                    }
                    untilUploadHasStarted.countDown();
                    return random.nextInt(256);
                }

            }))
            .build();
        final var responseToUpload = supplyAsync(() -> {
            try {
                return httpClient.send(upload, discarding());
            } catch (IOException | InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        }, command -> new Thread(command).start());
        final var download = HttpRequest
            .newBuilder()
            .uri(URI.create("https://localhost:8443/download"))
            .GET()
            .timeout(ofSeconds(10))
            .build();
        untilUploadHasStarted.await();
        final var responseToDownload = httpClient.send(download, ofString());
        untilDownloadIsFinished.countDown();
        assertEquals(200, responseToDownload.statusCode());
        assertEquals("applesauce", responseToDownload.body());
        assertEquals(200, responseToUpload.get().statusCode());
    }

    private SSLContext createSSLContextThatIgnoresInvalidCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        final var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {
            new X509TrustManager() {

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {

                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {

                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

            }
        }, new SecureRandom());
        return context;
    }

    @AfterEach
    public void stopServer() {
        server.stop();
    }

}
