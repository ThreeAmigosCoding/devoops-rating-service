package com.devoops.rating.mapper;

import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.model.Rating;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


import java.util.List;

@Mapper(componentModel = "spring")
public interface RatingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "guestId", ignore = true)
    @Mapping(target = "guestFirstName", ignore = true)
    @Mapping(target = "guestLastName", ignore = true)
    @Mapping(target = "targetType", source = "targetType")
    Rating toEntity(CreateRatingRequest request);

    RatingResponse toResponse(Rating rating);

    List<RatingResponse> toResponseList(List<Rating> ratings);
}
