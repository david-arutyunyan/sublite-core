package com.sublite.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Every log line for a request carries the same correlationId (MDC, so it
 * shows up in both the dev console pattern and the "docker" profile's
 * structured JSON automatically) - grep one id, see the whole request's
 * story. Echoed back as a response header too, so a client (or a manual
 * curl during testing) can quote it back when reporting an issue.
 *
 * Deliberately NOT a @Component: SecurityConfig wires this in explicitly
 * via addFilterBefore(). A Filter that's also a Spring bean gets
 * auto-registered a SECOND time by Boot's generic servlet filter
 * registration, running twice per request - keeping this filter bean-free
 * is what avoids that.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
