package top.aiolife.mcp.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import top.aiolife.sso.util.SecondaryLockSession;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 为 MCP 工具调用补充 Sa-Token 线程上下文。
 *
 * @author Lys
 * @date 2026/04/25
 */
public final class McpSaTokenScope {

    private McpSaTokenScope() {
    }

    private static <T> T runWithRequestAttributes(RequestAttributes requestAttributes,
                                                 ThrowingSupplier<T> supplier) throws Exception {
        RequestAttributes originalAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            RequestContextHolder.setRequestAttributes(requestAttributes);
        }
        try {
            return supplier.get();
        } finally {
            if (originalAttributes == null) {
                RequestContextHolder.resetRequestAttributes();
            } else {
                RequestContextHolder.setRequestAttributes(originalAttributes);
            }
        }
    }

    public static <T> T runWithContext(Object loginId,
                                       String sessionHash,
                                       RequestAttributes requestAttributes,
                                       ThrowingSupplier<T> supplier) throws Exception {
        return runWithRequestAttributes(requestAttributes, () -> runWithLoginIdInternal(loginId, sessionHash, supplier));
    }

    private static <T> T runWithLoginIdInternal(Object loginId, String sessionHash, ThrowingSupplier<T> supplier) throws Exception {
        if (loginId == null) {
            return supplier.get();
        }

        SaTokenContextForThreadLocalStorage.Box originalBox = SaTokenContextForThreadLocalStorage.getBox();
        SaTokenContextForThreadLocalStorage.setBox(SimpleSaRequest.INSTANCE, SimpleSaResponse.INSTANCE, new SimpleSaStorage());
        try {
            SaStorage storage = SaHolder.getStorage();
            String key = SecondaryLockSession.MCP_SESSION_HASH;
            Object previousHash = storage.get(key);
            if (sessionHash == null) storage.delete(key);
            else storage.set(key, sessionHash);
            try {
                return runWithSwitchLoginId(loginId, supplier);
            } finally {
                if (previousHash == null) storage.delete(key);
                else storage.set(key, previousHash);
            }
        } finally {
            restoreOriginalBox(originalBox);
        }
    }

    private static <T> T runWithSwitchLoginId(Object loginId, ThrowingSupplier<T> supplier) throws Exception {
        final Holder<T> holder = new Holder<>();
        final Holder<Exception> exceptionHolder = new Holder<>();
        StpUtil.switchTo(loginId, () -> {
            try {
                holder.value = supplier.get();
            } catch (Exception exception) {
                exceptionHolder.value = exception;
            }
        });
        if (exceptionHolder.value != null) {
            throw exceptionHolder.value;
        }
        return holder.value;
    }

    private static void restoreOriginalBox(SaTokenContextForThreadLocalStorage.Box originalBox) {
        if (originalBox == null) {
            SaTokenContextForThreadLocalStorage.clearBox();
            return;
        }
        SaTokenContextForThreadLocalStorage.boxThreadLocal.set(originalBox);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        T get() throws Exception;
    }

    private static final class Holder<T> {

        private T value;
    }

    private static final class SimpleSaStorage implements SaStorage {

        private final Map<String, Object> storage = new HashMap<>();

        @Override
        public Object getSource() {
            return storage;
        }

        @Override
        public Object get(String key) {
            return storage.get(key);
        }

        @Override
        public SaStorage set(String key, Object value) {
            storage.put(key, value);
            return this;
        }

        @Override
        public SaStorage delete(String key) {
            storage.remove(key);
            return this;
        }
    }

    private enum SimpleSaRequest implements SaRequest {
        INSTANCE;

        @Override
        public Object getSource() {
            return this;
        }

        @Override
        public String getParam(String name) {
            return null;
        }

        @Override
        public Collection<String> getParamNames() {
            return Collections.emptyList();
        }

        @Override
        public Map<String, String> getParamMap() {
            return Collections.emptyMap();
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public String getCookieValue(String name) {
            return null;
        }

        @Override
        public String getCookieFirstValue(String name) {
            return null;
        }

        @Override
        public String getCookieLastValue(String name) {
            return null;
        }

        @Override
        public String getRequestPath() {
            return "/mcp";
        }

        @Override
        public String getUrl() {
            return "/mcp";
        }

        @Override
        public String getMethod() {
            return "POST";
        }

        @Override
        public Object forward(String path) {
            return null;
        }
    }

    private enum SimpleSaResponse implements SaResponse {
        INSTANCE;

        @Override
        public Object getSource() {
            return this;
        }

        @Override
        public SaResponse setStatus(int sc) {
            return this;
        }

        @Override
        public SaResponse setHeader(String name, String value) {
            return this;
        }

        @Override
        public SaResponse addHeader(String name, String value) {
            return this;
        }

        @Override
        public Object redirect(String url) {
            return null;
        }
    }
}
