package com.curio.studio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Live snapshot of one Digest Studio task (digest generation or email send) as the
 * browser polls it. Every component but {@code type}/{@code state} is present only
 * for the phases that set it — a queued task has no {@code finishedAt}, a finished
 * one no {@code current}/{@code total} — so unset components are left out of the
 * JSON rather than sent as null.
 *
 * @param type       "digest" | "email"
 * @param state      IDLE | QUEUED | RUNNING | SUCCESS | SKIPPED | FAILED
 * @param phase      what the worker is doing right now, while running
 * @param current    progress numerator while running (topics summarized so far)
 * @param total      progress denominator; 0 when the phase has no meaningful count
 * @param message    outcome text once the task has finished
 * @param startedAt  ISO local date-time text (no zone)
 * @param finishedAt ISO local date-time text (no zone)
 * @param updatedAt  ISO local date-time text (no zone) of the last status write
 * @param digestId   the digest a successful generation produced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskStatus(
        String type,
        String state,
        String phase,
        Integer current,
        Integer total,
        String message,
        String startedAt,
        String finishedAt,
        String updatedAt,
        String digestId) {}
