package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.model.Company;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface UserFavoriteRepository {
    void setFavorite(String userId, String companyId, boolean saved);
    List<String> getFavoriteIds(String userId);
    boolean isFavorite(String userId, String companyId);
    void addFavorite(String userId, String companyId) throws ExecutionException, InterruptedException;
    void removeFavorite(String userId, String companyId);
    List<Company> getUserFavorites(String userId);
}
