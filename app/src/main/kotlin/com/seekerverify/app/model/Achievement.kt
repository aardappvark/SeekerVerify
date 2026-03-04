package com.seekerverify.app.model

enum class Achievement(
    val title: String,
    val description: String,
    val badge: String
) {
    FIRST_CHECK_IN("First Steps", "Complete your first daily check-in", "Day 1"),
    STREAK_7("Dedicated Seeker", "Maintain a 7-day check-in streak", "7 Days"),
    STREAK_30("Iron Will", "Maintain a 30-day check-in streak", "30 Days"),
    STAKER("Staking Pioneer", "Stake SKR tokens", "Staker"),
    DIAMOND_HANDS("Diamond Hands", "Hold over 10,000 SKR", "Hodler"),
    DOMAIN_OWNER("Named Seeker", "Own a .skr domain", "Domain"),
    PREDICTION_RUN("Crystal Ball", "Run your first Season 2 prediction", "Predict"),
    COMMUNITY_EXPLORER("Fleet Navigator", "View the community dashboard", "Fleet"),
    VANGUARD_OR_ABOVE("Top Tier", "Reach Vanguard tier or above", "Elite"),
    SEASON1_ANALYZED("Historian", "Analyze your Season 1 activity", "S1"),
    SIMULATOR_USED("Future Planner", "Use the What-If Simulator", "Planner"),
    STREAK_90("Unwavering", "Maintain a 90-day check-in streak", "90 Days"),
    HISTORY_CHECKED("Trend Watcher", "View your prediction trajectory", "Trends"),
    TIER_UPGRADED("Rising Star", "Improve your predicted tier", "Upgrade");
}
