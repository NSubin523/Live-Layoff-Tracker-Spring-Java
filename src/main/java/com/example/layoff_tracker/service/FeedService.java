package com.example.layoff_tracker.service;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.example.layoff_tracker.model.FeedCompany;
import com.example.layoff_tracker.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedService {
    private static final int DEFAULT_FEED_LIMIT = 25;
    private final FeedRepository feedRepository;

    public List<FeedResponseDto> getFeed() {
        try {
            return feedRepository.findLatest(DEFAULT_FEED_LIMIT).stream().map(FeedResponseDto::from).toList();
        } catch (RuntimeException e) {
            log.error("Failed to fetch feed", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to fetch feed. Please try again.", e);
        }
    }

    public FeedResponseDto getDetail(String companyId) {
        // Firestore document IDs are single path segments, not analytics UUIDs.
        if (companyId == null || companyId.isBlank() || companyId.contains("/")
                || companyId.equals(".") || companyId.equals("..") || companyId.matches("__.*__")
                || companyId.getBytes(StandardCharsets.UTF_8).length > 1500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid feed company ID");
        }
        Optional<FeedCompany> detail;
        try {
            detail = feedRepository.findById(companyId);
        } catch (RuntimeException e) {
            log.error("Failed to fetch feed details", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to fetch feed details. Please try again.", e);
        }
        return detail.map(FeedResponseDto::from).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feed item not found"));
    }
}
