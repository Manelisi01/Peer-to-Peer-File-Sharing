package com.p2p.filesharing;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Optional shared-secret check for the HTTP API.
 *
 * The desktop app generates a random token at every launch, hands it to the backend through
 * the P2P_API_TOKEN environment variable and attaches it to every request made by its own
 * window. Anything else on the machine (other programs, web pages in a browser) does not
 * know the token and is refused, so it cannot use the API to share or read local files.
 *
 * With no token configured (plain development runs) the filter lets everything through.
 */
@Component
public class ApiTokenFilter implements Filter {

    public static final String HEADER = "X-P2P-Token";

    private final byte[] token;

    public ApiTokenFilter(@Value("${p2p.api-token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (token.length == 0 || !isApiRequest(request) || hasValidToken(request)) {
            chain.doFilter(request, response);
            return;
        }
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    private static boolean isApiRequest(ServletRequest request) {
        String uri = ((HttpServletRequest) request).getRequestURI();
        return uri != null && (uri.equals("/api") || uri.startsWith("/api/"));
    }

    private boolean hasValidToken(ServletRequest request) {
        String supplied = ((HttpServletRequest) request).getHeader(HEADER);
        return supplied != null
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), token);
    }
}
