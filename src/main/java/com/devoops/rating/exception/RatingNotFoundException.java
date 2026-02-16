package com.devoops.rating.exception;

import java.util.UUID;

public class RatingNotFoundException extends RuntimeException {

    public RatingNotFoundException(UUID id) {
        super("Rating not found with id: " + id);
    }
}

