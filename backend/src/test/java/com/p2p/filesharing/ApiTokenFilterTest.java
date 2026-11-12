package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiTokenFilterTest {

    private final AtomicInteger passedThrough = new AtomicInteger();
    private final AtomicInteger errorStatus = new AtomicInteger();
    private final FilterChain chain = new FilterChain() {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            passedThrough.incrementAndGet();
        }
    };

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

    private static MockHttpServletRequest request(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader(ApiTokenFilter.HEADER, token);
        }
        return request;
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse() {
            @Override
            public void sendError(int status) {
                errorStatus.set(status);
                super.setStatus(status);
            }
        };
    }
}
