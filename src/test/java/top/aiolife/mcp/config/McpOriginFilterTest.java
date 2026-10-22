package top.aiolife.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpOriginFilterTest {

    private final McpOriginFilter filter = new McpOriginFilter();

    @Test
    void browserPostAndPreflightAreRejectedOnTransportEndpoint() throws Exception {
        for (String method : new String[] {"POST", "OPTIONS", "GET"}) {
            MockHttpServletRequest request = request(method, "/mcp");
            request.addHeader("Origin", "https://attacker.example");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean continued = new AtomicBoolean();

            filter.doFilter(request, response, (req, res) -> continued.set(true));

            assertEquals(403, response.getStatus());
            assertFalse(continued.get());
        }
    }

    @Test
    void nonBrowserMcpClientContinuesWithoutOrigin() throws Exception {
        MockHttpServletRequest request = request("POST", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertEquals(200, response.getStatus());
        assertTrue(continued.get());
    }

    @Test
    void managementEndpointsRemainAvailableToBrowser() throws Exception {
        MockHttpServletRequest request = request("POST", "/mcp/tools/call");
        request.addHeader("Origin", "https://app.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertEquals(200, response.getStatus());
        assertTrue(continued.get());
    }

    private static MockHttpServletRequest request(String method, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api" + servletPath);
        request.setContextPath("/api");
        request.setServletPath(servletPath);
        return request;
    }
}
