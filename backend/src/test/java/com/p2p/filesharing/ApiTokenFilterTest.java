package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ApiTokenFilterTest {

    private final AtomicInteger passedThrough = new AtomicInteger();
    private final AtomicInteger errorStatus = new AtomicInteger();
    private final FilterChain chain = (request, response) -> passedThrough.incrementAndGet();

    @Test
    void allowsEverythingWhenNoTokenIsConfigured() throws Exception {
        new ApiTokenFilter("").doFilter(request("/api/status", null), response(), chain);

        assertEquals(1, passedThrough.get());
        assertEquals(0, errorStatus.get());
    }

    @Test
    void refusesApiRequestsWithoutTheRightToken() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret");

        filter.doFilter(request("/api/status", null), response(), chain);
        filter.doFilter(request("/api/seeders", "wrong"), response(), chain);

        assertEquals(0, passedThrough.get());
        assertEquals(403, errorStatus.get());
    }

    @Test
    void letsApiRequestsWithTheTokenThrough() throws Exception {
        new ApiTokenFilter("secret").doFilter(request("/api/status", "secret"), response(), chain);

        assertEquals(1, passedThrough.get());
        assertEquals(0, errorStatus.get());
    }

    @Test
    void doesNotGuardTheStaticUiFiles() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret");

        filter.doFilter(request("/", null), response(), chain);
        filter.doFilter(request("/assets/index.js", null), response(), chain);

        assertEquals(2, passedThrough.get());
    }

    private static HttpServletRequest request(String uri, String token) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                ApiTokenFilterTest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> uri;
                    case "getHeader" -> ApiTokenFilter.HEADER.equalsIgnoreCase((String) args[0]) ? token : null;
                    default -> null;
                });
    }

    private HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                ApiTokenFilterTest.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendError")) {
                        errorStatus.set((Integer) args[0]);
                    }
                    return null;
                });
    }
}
