package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.dto.FeedResponseDto;
import java.util.List;

public interface FeedRepository {
    List<FeedResponseDto> findLatest(int limit);
}
