package com.devoops.rating.controller;

import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ratings")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    public ResponseEntity<RatingResponse> createRating(@Valid @RequestBody CreateRatingRequest request) {
        RatingResponse response = ratingService.createRating(request);
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
    public ResponseEntity<List<RatingResponse>> getRatingsByTargetId(@PathVariable UUID targetId) {
        List<RatingResponse> responses = ratingService.getRatingsByTargetId(targetId);
        return ResponseEntity.ok(responses);
    }



    @GetMapping("/guest/{guestId}")
    public ResponseEntity<List<RatingResponse>> getRatingsByGuestId(@PathVariable UUID guestId) {
        List<RatingResponse> responses = ratingService.getRatingsByGuestId(guestId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RatingResponse> updateRating(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRatingRequest request) {
        RatingResponse response = ratingService.updateRating(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRating(@PathVariable UUID id) {
        ratingService.deleteRating(id);
        return ResponseEntity.noContent().build();
    }
}

