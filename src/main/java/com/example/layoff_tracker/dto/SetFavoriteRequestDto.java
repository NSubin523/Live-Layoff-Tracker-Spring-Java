package com.example.layoff_tracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetFavoriteRequestDto {
    @NotNull(message = "Desired saved state is required")
    private Boolean saved;
}
