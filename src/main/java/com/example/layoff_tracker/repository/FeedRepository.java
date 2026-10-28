package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.model.FeedCompany;
import java.util.List;
import java.util.Optional;

public interface FeedRepository {
    List<FeedCompany> findLatest(int limit);
    Optional<FeedCompany> findById(String companyId);
}
