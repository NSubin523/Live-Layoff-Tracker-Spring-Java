package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.dto.SetFavoriteRequestDto;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
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

@Validated
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

    @PutMapping("/{companyId}")
    public ResponseEntity<ToggleFavoriteResponseDto> setFavorite(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable @NotBlank String companyId,
            @Valid @RequestBody SetFavoriteRequestDto request
    ) {
        String userId = authValidator.extractUserId(authHeader);
        return ResponseEntity.ok(favoriteService.setFavorite(userId, companyId, request.getSaved()));
    }

    @GetMapping("/ids")
    public ResponseEntity<List<String>> getFavoriteIds(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return ResponseEntity.ok(favoriteService.getFavoriteIds(authValidator.extractUserId(authHeader)));
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
