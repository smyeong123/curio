package com.curio.news.port.in;

import com.curio.news.entity.Digest;

/**
 * Outcome of one attempt to generate a user's digest for today. A digest is only
 * present when {@link #status()} is {@link Status#GENERATED}; every other status
 * says exactly why nothing was written, so callers (jobs, the admin batch, the
 * Studio progress UI) can count and message it without re-querying.
 */
public record DigestGeneration(Status status, Digest digest) {

    public enum Status {
        /** A new digest row was saved. */
        GENERATED,
        /** Today's digest already exists (or a concurrent run saved it first). */
        ALREADY_EXISTS,
        /** The user has no topic preferences yet. */
        NO_TOPICS,
        /** Every topic came back empty — provider failure, open circuit, or no news. */
        NOTHING_GENERATED
    }

    public static DigestGeneration generated(Digest digest) {
        return new DigestGeneration(Status.GENERATED, digest);
    }

    public static DigestGeneration alreadyExists() {
        return new DigestGeneration(Status.ALREADY_EXISTS, null);
    }

    public static DigestGeneration noTopics() {
        return new DigestGeneration(Status.NO_TOPICS, null);
    }

    public static DigestGeneration nothingGenerated() {
        return new DigestGeneration(Status.NOTHING_GENERATED, null);
    }

    public boolean generated() {
        return status == Status.GENERATED;
    }
}
