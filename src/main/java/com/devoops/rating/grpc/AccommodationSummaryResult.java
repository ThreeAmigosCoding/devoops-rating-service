package com.devoops.rating.grpc;

import java.util.UUID;

public record AccommodationSummaryResult(boolean found, String name, UUID hostId) {}
