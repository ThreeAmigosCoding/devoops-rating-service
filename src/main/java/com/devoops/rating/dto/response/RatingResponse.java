package com.devoops.rating.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.devoops.rating.model.RatingTargetType;

public record RatingResponse(
        UUID id,
        UUID targetId,
        RatingTargetType targetType,
        String guestFirstName,
        String guestLastName,
        UUID guestId,
        Integer score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }

