package com.curio.shared.config;

import com.curio.shared.security.CorrelationIdFilter;
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

/**
 * Tags every Sentry event with the MDC correlation id so issues in Sentry
 * can be cross-referenced against backend logs. Safe no-op when Sentry is
 * disabled (DSN unset) — {@link Sentry#configureScope} still executes but
 * events are dropped at the transport layer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SentryTaggingConfig extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (requestId != null) {
            Sentry.configureScope(scope -> scope.setTag("request_id", requestId));
        }
        chain.doFilter(request, response);
    }
}
