package com.devoops.rating.repository;

import com.devoops.rating.model.Rating;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatingRepository extends MongoRepository<Rating, UUID> {

    Optional<Rating> findByIdAndIsDeletedFalse(UUID id);

    List<Rating> findAllByIsDeletedFalse();

    List<Rating> findAllByTargetIdAndIsDeletedFalse(UUID targetId);

    List<Rating> findAllByGuestIdAndIsDeletedFalse(UUID guestId);

    Optional<Rating> findByTargetIdAndGuestIdAndIsDeletedFalse(UUID targetId, UUID guestId);

}

