package com.devoops.rating.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRatingRequest(
        @NotNull(message = "Target ID is required")
        UUID targetId,

        @NotBlank(message = "Guest first name is required")
        String guestFirstName,

        @NotBlank(message = "Guest last name is required")
        String guestLastName,

        @NotNull(message = "Guest ID is required")
        UUID guestId,

        @NotNull(message = "Score is required")
        @Min(value = 1, message = "Score must be at least 1")
        @Max(value = 5, message = "Score must be at most 5")
        Integer score
) {
}

