package com.curio.news.port.in;

/**
 * Callback for reporting per-topic progress while a single user's digest is
 * being generated, so a driving adapter (the user-facing Digest Studio) can
 * surface live progress. The scheduled/batch paths, which have no UI to update,
 * pass {@link #NOOP}.
 */
@FunctionalInterface
public interface DigestProgressListener {

    /**
     * Invoked just before each topic is summarized.
     *
     * @param index 1-based position of the topic about to be summarized
     * @param total total number of topics selected by the user
     * @param topic the topic name
     */
    void onTopic(int index, int total, String topic);

    /** No-op listener for callers that don't track progress (jobs, admin batch). */
    DigestProgressListener NOOP = (index, total, topic) -> { };
}
