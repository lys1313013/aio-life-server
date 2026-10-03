package top.aiolife.record.service;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.stereotype.Component;
import top.aiolife.core.util.FileContentPolicy;

/** B站图片专用下载器：固定 HTTPS 来源、受校验 DNS、禁代理及重定向。 */
@Component
public class VideoCoverDownloader {
    private static final OkHttpClient DNS_CLIENT = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .callTimeout(Duration.ofSeconds(10)).build();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(20)).dns(VideoCoverDownloader::resolvePublicAddresses).build();

    private static List<InetAddress> resolvePublicAddresses(String host) throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
        if (!addresses.isEmpty() && addresses.stream().allMatch(VideoCoverDownloader::isPublicAddress)) return addresses;
        // 本地代理 Fake-IP 也不能作为图片连接目标。通过固定 HTTPS DNS 服务取得真实地址，
        // 仍逐个验证并把同一批地址直接交给连接器，避免校验后再次解析造成 DNS rebinding。
        try (var response = DNS_CLIENT.newCall(new Request.Builder()
                .url("https://dns.google/resolve?type=A&name=" + java.net.URLEncoder.encode(host, java.nio.charset.StandardCharsets.UTF_8))
                .header("Accept", "application/dns-json").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("DNS_QUERY_FAILED");
            byte[] body = response.body().byteStream().readNBytes(65537);
            if (body.length > 65536) throw new java.io.IOException("DNS_RESPONSE_TOO_LARGE");
            var answers = com.alibaba.fastjson2.JSON.parseObject(body).getJSONArray("Answer");
            var result = new java.util.ArrayList<InetAddress>();
            if (answers != null) for (int i=0; i<answers.size(); i++) {
                var answer = answers.getJSONObject(i);
                String value = answer.getString("data");
                if (answer.getIntValue("type") != 1 || value == null || !value.matches("[0-9.]{7,15}")) continue;
                var address = InetAddress.getByName(value);
                if (!isPublicAddress(address)) throw new java.io.IOException("DNS_NON_PUBLIC_ADDRESS");
                result.add(address);
            }
            if (result.isEmpty()) throw new java.io.IOException("DNS_NO_PUBLIC_ADDRESS");
            return result;
        } catch (Exception e) {
            throw new UnknownHostException("无法解析公网图片地址");
        }
    }

    public String normalize(String source) {
        try {
            URI uri = URI.create(source == null ? "" : source.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || !(host.equals("hdslb.com") || host.endsWith(".hdslb.com"))
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawPath() == null || !uri.getRawPath().startsWith("/bfs/")) {
                throw new IllegalArgumentException();
            }
            // 仅将已验证的 B站 HTTP 链接升级 HTTPS，不更改图片参数。
            return "https://" + host + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("视频封面仅支持B站图片来源", e);
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 255, second = bytes[1] & 255;
        if (bytes.length == 4) {
            return first != 0 && first != 127 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 198 && (second == 18 || second == 19));
        }
        // 只接受 IPv6 全球单播，排除 ULA、映射地址、NAT64、链路本地等。
        return (first & 0xe0) == 0x20;
    }

    public byte[] download(String source) {
        String url = normalize(source);
        try (var response = client.newCall(new Request.Builder().url(url)
                .header("User-Agent", "AIO-Life/1.0")
                .header("Accept", "image/*").build()).execute()) {
            if (response.code() != 200 || response.body() == null)
                throw new IllegalStateException("IMAGE_HTTP_" + response.code());
            if (response.body().contentLength() > FileContentPolicy.MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("IMAGE_TOO_LARGE");
            byte[] bytes = response.body().byteStream().readNBytes(FileContentPolicy.MAX_IMAGE_BYTES + 1);
            FileContentPolicy.requireImage(bytes);
            return bytes;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("IMAGE_DOWNLOAD_FAILED", e);
        }
    }
}
