package com.devoops.rating.grpc;

import java.util.UUID;

public record UserSummaryResult(
        boolean found,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean isDeleted
) {}
