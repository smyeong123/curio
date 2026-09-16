package com.curio.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        // Query strings are NOT logged: several permitAll GETs carry secrets in
        // the query (?token= on unsubscribe, ?code= deep links). Logging them
        // would drop replayable credentials into plaintext app logs.
        filter.setIncludeQueryString(false);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        filter.setMaxPayloadLength(1000);
        filter.setAfterMessagePrefix("REQUEST: ");
        return filter;
    }
}
