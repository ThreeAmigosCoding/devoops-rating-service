package com.devoops.rating.dto.request;

import com.devoops.rating.model.RatingTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRatingRequest(
        @NotNull(message = "Target ID is required")
        UUID targetId,

        @NotNull(message = "Target type is required")
        RatingTargetType targetType,

        @NotNull(message = "Score is required")
        @Min(value = 1, message = "Score must be at least 1")
        @Max(value = 5, message = "Score must be at most 5")
        Integer score
) {
}
