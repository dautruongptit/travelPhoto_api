package com.travelalbum.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum StoragePlanDefinition {
    PLAN_1W("plan-1w", "1 tuần", 7, 100L, 10_000L),
    PLAN_1M("plan-1m", "1 tháng", 30, 100L, 25_000L),
    PLAN_6M("plan-6m", "6 tháng", 180, 200L, 120_000L),
    PLAN_1Y("plan-1y", "1 năm", 365, 200L, 195_000L);

    private final String id;
    private final String label;
    private final int durationDays;
    private final long storageGb;
    private final long priceVnd;

    public static Optional<StoragePlanDefinition> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.getId().equalsIgnoreCase(id.trim()))
                .findFirst();
    }
}
