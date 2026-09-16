package com.curio.shared.exception;

/**
 * Small helpers for turning exceptions into human-readable messages suitable
 * for surfacing to admin users. Walks the cause chain so that wrapped
 * RestClient / JPA / circuit-breaker exceptions still yield the underlying
 * provider error (e.g. Claude's "401 invalid x-api-key") instead of a
 * generic "Request processing failed".
 */
public final class RootCauses {

    private RootCauses() {}

    private static final int MAX_LEN = 300;

    public static String describe(Throwable t) {
        if (t == null) return "unknown error";
        Throwable cur = t;
        for (int i = 0; i < 10 && cur.getCause() != null && cur.getCause() != cur; i++) {
            cur = cur.getCause();
        }
        String cls = cur.getClass().getSimpleName();
        String msg = cur.getMessage();
        String combined = (msg == null || msg.isBlank()) ? cls : cls + ": " + msg.trim();
        combined = combined.replaceAll("\\s+", " ");
        if (combined.length() > MAX_LEN) {
            combined = combined.substring(0, MAX_LEN - 1) + "…";
        }
        return combined;
    }
}
