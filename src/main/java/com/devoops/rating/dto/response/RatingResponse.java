package com.devoops.rating.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingResponse(
        UUID id,
        UUID targetId,
        String guestFirstName,
        String guestLastName,
        UUID guestId,
        Integer score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }

