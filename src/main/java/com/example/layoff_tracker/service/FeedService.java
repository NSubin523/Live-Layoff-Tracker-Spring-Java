package com.example.layoff_tracker.service;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.example.layoff_tracker.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedService {
    private static final int DEFAULT_FEED_LIMIT = 25;
    private final FeedRepository feedRepository;

    public List<FeedResponseDto> getFeed() {
        try {
            return feedRepository.findLatest(DEFAULT_FEED_LIMIT);
        } catch (RuntimeException e) {
            log.error("Failed to fetch feed", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to fetch feed. Please try again.", e);
        }
    }
}
