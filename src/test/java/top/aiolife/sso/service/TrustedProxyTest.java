package top.aiolife.sso.service;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrustedProxyTest {
    @Test
    void testClientIp_默认配置与未授权代理均忽略转发头() throws Exception {
        assertEquals("127.0.0.1", remoteAddress(new MockEnvironment()));
        var nativeMode = new MockEnvironment().withProperty("AIO_LIFE_FORWARD_HEADERS_STRATEGY", "NATIVE");
        assertEquals("127.0.0.1", remoteAddress(nativeMode));
        assertEquals("127.0.0.1", remoteAddress(
                nativeMode.withProperty("AIO_LIFE_TRUSTED_PROXY_REGEX", "192[.]0[.]2[.]80")));
    }

    @Test
    void testClientIp_可信代理链使用最靠近入口的非代理来源() throws Exception {
        var env = new MockEnvironment().withProperty("AIO_LIFE_FORWARD_HEADERS_STRATEGY", "NATIVE")
                .withProperty("AIO_LIFE_TRUSTED_PROXY_REGEX", "127[.]0[.]0[.]1|192[.]0[.]2[.]80");
        assertEquals("198.51.100.10", remoteAddress(env));
    }

    private String remoteAddress(MockEnvironment env) throws Exception {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        sources.forEach(env.getPropertySources()::addLast);
        var config = Binder.get(env).bind("server", Bindable.of(ServerProperties.class)).get();
        var factory = new TomcatServletWebServerFactory();
        new TomcatWebServerFactoryCustomizer(env, config).customize(factory);
        factory.setAddress(InetAddress.getByName("127.0.0.1"));
        factory.setPort(0);
        var server = factory.getWebServer(context -> context.addServlet("client-ip", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.getWriter().write(request.getRemoteAddr());
            }
        }).addMapping("/"));
        try (var client = HttpClient.newHttpClient()) {
            server.start();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort()))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-Forwarded-For", "203.0.113.9, 198.51.100.10, 192.0.2.80")
                    .header("Proxy-Client-IP", "203.0.113.10").build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } finally {
            server.stop();
        }
    }
}
