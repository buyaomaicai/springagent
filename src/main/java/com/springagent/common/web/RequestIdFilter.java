package com.springagent.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    private static final String REQUEST_ATTRIBUTE =
            RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern VALID_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(RequestIdContext.MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object existingRequestId = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existingRequestId instanceof String requestId) {
            return requestId;
        }

        String providedRequestId = request.getHeader(HEADER_NAME);
        if (providedRequestId != null
                && VALID_REQUEST_ID.matcher(providedRequestId).matches()) {
            return providedRequestId;
        }

        return UUID.randomUUID().toString();
    }
}
