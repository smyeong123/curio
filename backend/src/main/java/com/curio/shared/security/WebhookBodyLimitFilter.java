package com.curio.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Caps the request body size on the public (permitAll) endpoints — webhooks,
 * auth, and unsubscribe. These read the body (webhook reads the raw String
 * before its HMAC check even runs), so without a cap an unauthenticated caller
 * could POST multi-gigabyte bodies and pressure the heap. Real payloads here are
 * a few KB; 256 KB is generous headroom. This is the app-layer backstop for the
 * nginx edge's own client_max_body_size — it still holds on a direct/internal
 * deploy where the reverse proxy isn't in front.
 *
 * Declared Content-Length over the cap is rejected up front with 413; chunked
 * bodies (no Content-Length) are capped while being read.
 */
@Component
public class WebhookBodyLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookBodyLimitFilter.class);

    static final long MAX_BODY_BYTES = 256 * 1024;

    private static boolean isPublicBodyRoute(String uri) {
        return uri.startsWith("/api/v1/webhooks/")
                || uri.startsWith("/api/v1/auth/")
                || uri.equals("/api/v1/user/unsubscribe");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isPublicBodyRoute(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_BODY_BYTES) {
            log.warn("Rejected webhook request with declared body size {} bytes", declaredLength);
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return;
        }

        chain.doFilter(new BodyLimitedRequest(request), response);
    }

    private static final class BodyLimitedRequest extends HttpServletRequestWrapper {
        BodyLimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream());
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private long bytesRead;

        LimitedServletInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        private void count(long n) throws IOException {
            if (n > 0) {
                bytesRead += n;
                if (bytesRead > MAX_BODY_BYTES) {
                    throw new IOException("Webhook request body exceeds " + MAX_BODY_BYTES + " bytes");
                }
            }
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            count(n);
            return n;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
