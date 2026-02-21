package com.devoops.rating.dto.message;

import java.util.UUID;

public record HostRatedMessage(UUID userId, String userEmail, String guestName, Integer rating, String comment) {}
