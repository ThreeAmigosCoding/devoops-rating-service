package com.devoops.rating.dto.response;

import java.util.List;

public record RatingsSummaryResponse(
        List<RatingResponse> ratings,
        double averageScore,
        int totalCount
) {}
