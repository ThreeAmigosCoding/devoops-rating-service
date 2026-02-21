package com.devoops.rating.grpc;

public record ReservationEligibilityResult(boolean eligible, String reason) {}
