package com.springagent.common.web;

import java.util.UUID;
import org.slf4j.MDC;

public final class RequestIdContext {

    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String current() {
        String requestId = MDC.get(MDC_KEY);
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }
}
