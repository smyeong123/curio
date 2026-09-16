package com.curio.shared.security;

import io.sentry.Sentry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : sanitize(incoming);

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        Sentry.configureScope(scope -> {
            scope.setTag("request_id", requestId);
            scope.setTag("route", request.getMethod() + " " + request.getRequestURI());
        });
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            Sentry.configureScope(scope -> scope.removeTag("request_id"));
        }
    }

    private String sanitize(String value) {
        String trimmed = value.trim();
        if (trimmed.length() > 128) {
            trimmed = trimmed.substring(0, 128);
        }
        return trimmed.replaceAll("[\\r\\n\\t]", "_");
    }
}
