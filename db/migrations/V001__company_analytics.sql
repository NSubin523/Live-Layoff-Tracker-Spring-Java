-- Apply explicitly to the intended database. No startup migration or seed data.
BEGIN;
CREATE SCHEMA company_analytics;

CREATE TABLE company_analytics.companies (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    domain TEXT,
    logo_url TEXT,
    listing_status TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (listing_status IN ('PUBLIC', 'PRIVATE', 'UNKNOWN')),
    external_identifiers JSONB NOT NULL DEFAULT '{}'
);
CREATE TABLE company_analytics.company_legacy_refs (
    namespace TEXT NOT NULL,
    legacy_id TEXT NOT NULL,
    company_id UUID NOT NULL REFERENCES company_analytics.companies(id),
    PRIMARY KEY (namespace, legacy_id)
);
CREATE INDEX ON company_analytics.company_legacy_refs(company_id);

CREATE TABLE company_analytics.sources (
    id UUID PRIMARY KEY,
    publisher TEXT NOT NULL CHECK (length(trim(publisher)) > 0),
    url TEXT NOT NULL CHECK (url ~ '^https?://'),
    source_type TEXT NOT NULL CHECK (source_type IN ('COMPANY_DISCLOSURE', 'REGULATORY_FILING', 'WARN_NOTICE', 'REPORTING', 'LICENSED_ESTIMATE')),
    published_on DATE,
    retrieved_at TIMESTAMPTZ NOT NULL,
    provider TEXT,
    provider_record_id TEXT,
    UNIQUE (provider, provider_record_id)
);

CREATE TABLE company_analytics.workforce_observations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company_analytics.companies(id),
    employee_count BIGINT CHECK (employee_count >= 0),
    range_min BIGINT CHECK (range_min >= 0),
    range_max BIGINT CHECK (range_max >= range_min),
    as_of DATE NOT NULL,
    geography TEXT NOT NULL,
    organizational_scope TEXT NOT NULL,
    employee_definition TEXT NOT NULL,
    classification TEXT NOT NULL CHECK (classification IN ('COMPANY_REPORTED', 'ESTIMATED')),
    source_id UUID NOT NULL REFERENCES company_analytics.sources(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((employee_count IS NOT NULL AND range_min IS NULL AND range_max IS NULL)
        OR (employee_count IS NULL AND range_min IS NOT NULL AND range_max IS NOT NULL))
);
CREATE INDEX ON company_analytics.workforce_observations(company_id, as_of DESC, id DESC);

CREATE TABLE company_analytics.layoff_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company_analytics.companies(id),
    announced_on DATE NOT NULL,
    effective_from DATE,
    effective_to DATE,
    affected_count BIGINT CHECK (affected_count >= 0),
    range_min BIGINT CHECK (range_min >= 0),
    range_max BIGINT CHECK (range_max >= range_min),
    status TEXT NOT NULL CHECK (status IN ('CONFIRMED', 'RUMORED')),
    execution_state TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (execution_state IN ('PLANNED', 'COMPLETED', 'UNKNOWN')),
    record_state TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (record_state IN ('ACTIVE', 'CANCELLED', 'SUPERSEDED')),
    overlap_state TEXT NOT NULL DEFAULT 'UNRESOLVED' CHECK (overlap_state IN ('VERIFIED_DISTINCT', 'UNRESOLVED')),
    geography TEXT NOT NULL,
    organizational_scope TEXT NOT NULL,
    employee_definition TEXT NOT NULL,
    primary_source_id UUID NOT NULL REFERENCES company_analytics.sources(id),
    provider TEXT,
    provider_record_id TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_record_id),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CHECK ((affected_count IS NOT NULL AND range_min IS NULL AND range_max IS NULL)
        OR (affected_count IS NULL AND range_min IS NULL AND range_max IS NULL)
        OR (affected_count IS NULL AND range_min IS NOT NULL AND range_max IS NOT NULL))
);
CREATE INDEX ON company_analytics.layoff_events(company_id, announced_on DESC, id DESC) WHERE record_state = 'ACTIVE';
-- Preserve the previous claim on correction/cancellation/deletion. This audit
-- table deliberately retains snapshots even if an event is removed later.
CREATE TABLE company_analytics.layoff_event_revisions (
    revision_id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    operation TEXT NOT NULL CHECK (operation IN ('UPDATE', 'DELETE')),
    snapshot JSONB NOT NULL
);
CREATE INDEX ON company_analytics.layoff_event_revisions(event_id, revision_id);
CREATE FUNCTION company_analytics.archive_layoff_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW IS NOT DISTINCT FROM OLD THEN
        RETURN NEW;
    END IF;
    INSERT INTO company_analytics.layoff_event_revisions(event_id, operation, snapshot)
    VALUES (OLD.id, TG_OP, to_jsonb(OLD));
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER archive_layoff_event BEFORE UPDATE OR DELETE ON company_analytics.layoff_events
FOR EACH ROW EXECUTE FUNCTION company_analytics.archive_layoff_event();

CREATE TABLE company_analytics.layoff_event_sources (
    event_id UUID NOT NULL REFERENCES company_analytics.layoff_events(id),
    source_id UUID NOT NULL REFERENCES company_analytics.sources(id),
    claim_reference TEXT,
    PRIMARY KEY (event_id, source_id)
);

-- One acquisition/coverage summary per section. Provider-specific details can be
-- added when providers are selected. All coverage initially remains incomplete.
CREATE TABLE company_analytics.analytics_coverage (
    company_id UUID NOT NULL REFERENCES company_analytics.companies(id),
    section TEXT NOT NULL CHECK (section IN ('WORKFORCE', 'LAYOFFS')),
    coverage_from DATE,
    coverage_to DATE,
    scope TEXT NOT NULL,
    completeness TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (completeness IN ('PARTIAL', 'UNKNOWN')),
    last_successful_refresh_at TIMESTAMPTZ,
    refresh_due_at TIMESTAMPTZ,
    last_attempt_outcome TEXT CHECK (last_attempt_outcome IN ('SUCCESS', 'FAILED')),
    PRIMARY KEY (company_id, section),
    CHECK (coverage_to IS NULL OR coverage_from IS NULL OR coverage_to >= coverage_from),
    CHECK (refresh_due_at IS NULL OR last_successful_refresh_at IS NOT NULL),
    CHECK (refresh_due_at IS NULL OR refresh_due_at >= last_successful_refresh_at)
);
COMMIT;
