package com.devoops.rating.controller;

import com.devoops.rating.config.RequireRole;
import com.devoops.rating.config.UserContext;
import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.response.RatingsSummaryResponse;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rating")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @RequireRole("GUEST")
    public ResponseEntity<RatingResponse> createRating(
            @Valid @RequestBody CreateRatingRequest request,
            UserContext userContext) {
        RatingResponse response = ratingService.createRating(request, userContext);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RatingResponse> getRatingById(@PathVariable UUID id) {
        RatingResponse response = ratingService.getRatingById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RatingResponse>> getAllRatings() {
        List<RatingResponse> responses = ratingService.getAllRatings();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/target/{targetId}")
    public ResponseEntity<RatingsSummaryResponse> getRatingsByTargetId(@PathVariable UUID targetId) {
        RatingsSummaryResponse response = ratingService.getRatingsByTargetId(targetId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/guest")
    @RequireRole("GUEST")
    public ResponseEntity<List<RatingResponse>> getRatingsByGuestId(UserContext userContext) {
        List<RatingResponse> responses = ratingService.getRatingsByGuestId(userContext.userId());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    @RequireRole("GUEST")
    public ResponseEntity<RatingResponse> updateRating(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRatingRequest request,
            UserContext userContext) {
        RatingResponse response = ratingService.updateRating(id, request, userContext);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @RequireRole("GUEST")
    public ResponseEntity<Void> deleteRating(@PathVariable UUID id, UserContext userContext) {
        ratingService.deleteRating(id, userContext);
        return ResponseEntity.noContent().build();
    }
}
