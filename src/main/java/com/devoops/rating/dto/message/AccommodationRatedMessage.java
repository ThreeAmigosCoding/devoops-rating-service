package com.devoops.rating.dto.message;

import java.util.UUID;

public record AccommodationRatedMessage(UUID userId, String userEmail, String guestName, String accommodationName, Integer rating, String comment) {}
