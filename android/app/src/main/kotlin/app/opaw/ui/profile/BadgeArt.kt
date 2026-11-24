package app.opaw.ui.profile

import androidx.annotation.DrawableRes
import app.opaw.R
import app.opaw.core.Badge
import app.opaw.core.BadgeLevel
import app.opaw.core.Tier

/**
 * Medal art for every badge level (design v5, 2g): flat orange medals made in Higgsfield, recoloured
 * bronze, silver and gold. Sources and prompts live in docs/brand/badges.
 */
private val medals: Map<String, Int> = mapOf(
    "badge_paw_prints_bronze" to R.drawable.badge_paw_prints_bronze,
    "badge_paw_prints_silver" to R.drawable.badge_paw_prints_silver,
    "badge_paw_prints_gold" to R.drawable.badge_paw_prints_gold,
    "badge_care_streak_bronze" to R.drawable.badge_care_streak_bronze,
    "badge_care_streak_silver" to R.drawable.badge_care_streak_silver,
    "badge_care_streak_gold" to R.drawable.badge_care_streak_gold,
    "badge_walks_bronze" to R.drawable.badge_walks_bronze,
    "badge_walks_silver" to R.drawable.badge_walks_silver,
    "badge_walks_gold" to R.drawable.badge_walks_gold,
    "badge_long_walk_bronze" to R.drawable.badge_long_walk_bronze,
    "badge_long_walk_silver" to R.drawable.badge_long_walk_silver,
    "badge_long_walk_gold" to R.drawable.badge_long_walk_gold,
    "badge_distance_bronze" to R.drawable.badge_distance_bronze,
    "badge_distance_silver" to R.drawable.badge_distance_silver,
    "badge_distance_gold" to R.drawable.badge_distance_gold,
    "badge_busy_month_bronze" to R.drawable.badge_busy_month_bronze,
    "badge_busy_month_silver" to R.drawable.badge_busy_month_silver,
    "badge_busy_month_gold" to R.drawable.badge_busy_month_gold,
    "badge_early_bird_bronze" to R.drawable.badge_early_bird_bronze,
    "badge_early_bird_silver" to R.drawable.badge_early_bird_silver,
    "badge_early_bird_gold" to R.drawable.badge_early_bird_gold,
    "badge_walk_streak_bronze" to R.drawable.badge_walk_streak_bronze,
    "badge_walk_streak_silver" to R.drawable.badge_walk_streak_silver,
    "badge_walk_streak_gold" to R.drawable.badge_walk_streak_gold,
    "badge_squeaky_clean_bronze" to R.drawable.badge_squeaky_clean_bronze,
    "badge_squeaky_clean_silver" to R.drawable.badge_squeaky_clean_silver,
    "badge_squeaky_clean_gold" to R.drawable.badge_squeaky_clean_gold,
    "badge_memory_keeper_bronze" to R.drawable.badge_memory_keeper_bronze,
    "badge_memory_keeper_silver" to R.drawable.badge_memory_keeper_silver,
    "badge_memory_keeper_gold" to R.drawable.badge_memory_keeper_gold,
    "badge_health_check_bronze" to R.drawable.badge_health_check_bronze,
    "badge_health_check_silver" to R.drawable.badge_health_check_silver,
    "badge_health_check_gold" to R.drawable.badge_health_check_gold,
    "badge_pack_leader_bronze" to R.drawable.badge_pack_leader_bronze,
    "badge_pack_leader_silver" to R.drawable.badge_pack_leader_silver,
    "badge_pack_leader_gold" to R.drawable.badge_pack_leader_gold,
)

@get:DrawableRes
val BadgeLevel.medalRes: Int get() = medals.getValue(art)

@DrawableRes
fun Badge.medalRes(tier: Tier): Int = level(tier).medalRes
