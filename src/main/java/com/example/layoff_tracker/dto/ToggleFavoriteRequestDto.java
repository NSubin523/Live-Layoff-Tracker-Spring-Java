package com.example.layoff_tracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToggleFavoriteRequestDto {

    @NotBlank(message = "Company UUID cannot be blank")
    private String companyId;

}
