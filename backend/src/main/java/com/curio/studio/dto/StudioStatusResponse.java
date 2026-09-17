package com.curio.studio.dto;

/** Body of {@code GET /api/v1/studio/status}: both task snapshots plus page context. */
public record StudioStatusResponse(TaskStatus digest, TaskStatus email, StudioOverview overview) {}
