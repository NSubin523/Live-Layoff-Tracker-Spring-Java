package com.example.layoff_tracker.mapper;

import com.example.layoff_tracker.model.Company;
import com.example.layoff_tracker.model.LayoffStatus;
import com.example.layoff_tracker.model.TrendDirection;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;

import java.time.Instant;

public class CompanyMapper {
    public static Company fromDocument(DocumentSnapshot doc) {
        if (!doc.exists()) {
            return null;
        }

        Timestamp reportedAtTimestamp = doc.getTimestamp("reported_at");
        Instant reportedAtInstant = (reportedAtTimestamp != null)
                ? reportedAtTimestamp.toDate().toInstant()
                : Instant.now();

        Long impactLong = doc.getLong("impact_count");
        Integer impactCount = (impactLong != null) ? impactLong.intValue() : null;

        return Company.builder()
                .id(doc.getId())
                .companyName(doc.getString("company_name"))
                .impactCount(impactCount)
                .layoffStatus(LayoffStatus.fromString(doc.getString("status")))
                .industry(doc.getString("industry"))
                .location(doc.getString("location"))
                .reportedAt(reportedAtInstant)
                .logoUrl(doc.getString("logo_url"))
                .trendDirection(TrendDirection.fromString(doc.getString("trend_direction")))
                .build();
    }
}
