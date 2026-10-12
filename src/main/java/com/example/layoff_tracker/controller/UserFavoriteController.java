package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.dto.ToggleFavoriteRequestDto;
import com.example.layoff_tracker.dto.ToggleFavoriteResponseDto;
import com.example.layoff_tracker.model.Company;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import com.example.layoff_tracker.service.UserFavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/favorites")
@RequiredArgsConstructor
public class UserFavoriteController {

    private final UserFavoriteService favoriteService;
    private final FirebaseAuthValidator authValidator;

    @PostMapping("/toggle")
    public ResponseEntity<ToggleFavoriteResponseDto> toggleFavorite(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ToggleFavoriteRequestDto request
    ) {
        // Authenticate request and resolve trusted user UID
        String userId = authValidator.extractUserId(authHeader);

        // Execute toggle operation via service layer
        ToggleFavoriteResponseDto response = favoriteService.toggleFavorite(userId, request.getCompanyId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Company>> getFavorites(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        // Authenticate request and resolve trusted user UID
        String userId = authValidator.extractUserId(authHeader);

        // Fetch populated bookmarks
        List<Company> favorites = favoriteService.getUserFavorites(userId);
        return ResponseEntity.ok(favorites);
    }

}
