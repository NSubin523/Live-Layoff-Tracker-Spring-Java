package com.example.layoff_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToggleFavoriteResponseDto {
    private String companyId;
    private boolean saved;
    private String message;
}
