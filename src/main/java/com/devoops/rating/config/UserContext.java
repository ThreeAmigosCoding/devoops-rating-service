package com.devoops.rating.config;

import java.util.UUID;

public record UserContext(UUID userId, String role) { }
