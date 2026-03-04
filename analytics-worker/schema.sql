-- Anonymous geo-analytics schema for AardAppvark Toolkit
-- Stores ONLY aggregate counts per event/country/city/date
-- NO user IDs, device IDs, IP addresses, or PII of any kind

CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    country TEXT NOT NULL DEFAULT 'XX',
    city TEXT NOT NULL DEFAULT 'Unknown',
    region TEXT NOT NULL DEFAULT 'Unknown',
    count INTEGER NOT NULL DEFAULT 1,
    date TEXT NOT NULL,
    UNIQUE(event_type, country, city, date)
);

CREATE INDEX IF NOT EXISTS idx_events_date ON events(date);
CREATE INDEX IF NOT EXISTS idx_events_type ON events(event_type);
CREATE INDEX IF NOT EXISTS idx_events_country ON events(country);

-- Leaderboard: anonymous score distribution (no wallet/user data)
CREATE TABLE IF NOT EXISTS leaderboard (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    score_bucket TEXT NOT NULL,
    tier TEXT NOT NULL,
    country TEXT NOT NULL DEFAULT 'XX',
    date TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    UNIQUE(score_bucket, tier, country, date)
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_date ON leaderboard(date);
CREATE INDEX IF NOT EXISTS idx_leaderboard_tier ON leaderboard(tier);

-- Summary view: daily totals by country
CREATE VIEW IF NOT EXISTS daily_country_totals AS
SELECT
    date,
    country,
    SUM(count) as total_events,
    COUNT(DISTINCT event_type) as unique_event_types
FROM events
GROUP BY date, country
ORDER BY date DESC, total_events DESC;

-- Summary view: event type breakdown
CREATE VIEW IF NOT EXISTS event_type_totals AS
SELECT
    event_type,
    SUM(count) as total,
    COUNT(DISTINCT country) as countries,
    COUNT(DISTINCT city) as cities
FROM events
GROUP BY event_type
ORDER BY total DESC;
