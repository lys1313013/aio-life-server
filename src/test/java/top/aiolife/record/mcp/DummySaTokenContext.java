package top.aiolife.record.mcp;

import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class DummySaTokenContext implements SaTokenContext {
    private final SaStorage storage = new SaStorage() {
        private final Map<String, Object> map = new HashMap<>();
        @Override public Object getSource() { return map; }
        @Override public SaStorage set(String key, Object value) { map.put(key, value); return this; }
        @Override public Object get(String key) { return map.get(key); }
        @Override public SaStorage delete(String key) { map.remove(key); return this; }
    };
    
    private final SaRequest request = (SaRequest) Proxy.newProxyInstance(
            SaRequest.class.getClassLoader(),
            new Class[]{SaRequest.class},
            (proxy, method, args) -> null
    );
    
    private final SaResponse response = (SaResponse) Proxy.newProxyInstance(
            SaResponse.class.getClassLoader(),
            new Class[]{SaResponse.class},
            (proxy, method, args) -> null
    );

    @Override
    public SaRequest getRequest() { return request; }
    @Override
    public SaResponse getResponse() { return response; }
    @Override
    public SaStorage getStorage() { return storage; }
    @Override
    public boolean matchPath(String pattern, String path) { return false; }
}
