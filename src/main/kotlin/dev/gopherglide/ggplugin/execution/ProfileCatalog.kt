package dev.gopherglide.ggplugin.execution

/**
 * Categories mirror the grouping used at gopherglide.dev/profiles.
 */
enum class ProfileCategory(val displayName: String) {
    ECOMMERCE("E-Commerce & High-Demand Events"),
    STANDARD_CI("Standard Testing & CI/CD"),
    RESILIENCE_CHAOS("Resilience & Chaos"),
    AUTO_SCALING("Auto-Scaling & Infrastructure"),
    SPECIALIZED("Specialized Traffic"),
}

data class GgProfile(
    val name: String,
    val description: String,
    val category: ProfileCategory,
    val defaultDuration: String,
    val defaultPeakRps: Int,
)

/**
 * Static catalog of `gg`'s 21 built-in load profiles (`gg profile list`).
 * Custom profiles exported via `gg profile export` are not represented here.
 */
object ProfileCatalog {
    /** Used for the zero-config default Run click when no sibling `.gg.yaml` exists. */
    const val DEFAULT_ZERO_CONFIG_PROFILE = "smoke"

    val profiles: List<GgProfile> = listOf(
        GgProfile("flash-sale", "Massive instant spike from 0 to peak, hold for the full run, then instant drop.", ProfileCategory.ECOMMERCE, "3m", 2000),
        GgProfile("black-friday", "Extended heavy baseline load with two unpredictable sharp checkout-rush spikes.", ProfileCategory.ECOMMERCE, "17m", 2500),
        GgProfile("ticket-release", "Instantaneous, extreme spike held for a narrow 1m window.", ProfileCategory.ECOMMERCE, "1m", 5000),
        GgProfile("inventory-drop", "High immediate load with a slow, organic tail-off as stock depletes.", ProfileCategory.ECOMMERCE, "16m", 1500),

        GgProfile("canary", "Very brief, very low RPS post-deployment verification.", ProfileCategory.STANDARD_CI, "30s", 5),
        GgProfile("smoke", "Quick health check at low RPS — the lightest possible test.", ProfileCategory.STANDARD_CI, "10s", 10),
        GgProfile("load", "Standard baseline test: gradual ramp up, sustained hold, gradual ramp down.", ProfileCategory.STANDARD_CI, "2m", 100),
        GgProfile("stress", "Aggressive staircase ramp designed to find the breaking point.", ProfileCategory.STANDARD_CI, "3m30s", 2000),
        GgProfile("soak", "Sustained, moderate load over 1h+ to surface leaks and GC pressure.", ProfileCategory.STANDARD_CI, "70m", 200),
        GgProfile("endurance", "Soak test at a dangerously high capacity for 2h.", ProfileCategory.STANDARD_CI, "130m", 500),

        GgProfile("ddos", "Sustained high-volume attack simulation with no ramp.", ProfileCategory.RESILIENCE_CHAOS, "10m", 5000),
        GgProfile("spike", "Single sharp jump and drop to test queue buffering and recovery.", ProfileCategory.RESILIENCE_CHAOS, "3m30s", 1000),
        GgProfile("burst", "Repeating series of short spikes to test queue/worker-pool drain behaviour.", ProfileCategory.RESILIENCE_CHAOS, "3m45s", 500),
        GgProfile("retry-storm", "Fast repeating spikes simulating aggressive client retry amplification.", ProfileCategory.RESILIENCE_CHAOS, "4m", 800),
        GgProfile("chaos", "Moderate RPS with extremely high jitter to simulate irregular arrivals.", ProfileCategory.RESILIENCE_CHAOS, "10m", 200),

        GgProfile("step-up", "Staircase ramp to gracefully find the breaking point, step by step.", ProfileCategory.AUTO_SCALING, "14m", 1000),
        GgProfile("wave", "Oscillating sine-wave RPS to test auto-scaling elasticity in and out.", ProfileCategory.AUTO_SCALING, "12m", 500),
        GgProfile("scale-down", "Starts at full peak, then slowly ramps to zero to test graceful draining.", ProfileCategory.AUTO_SCALING, "10m", 1000),

        GgProfile("crawler", "Simulates steady search-engine bot traffic: constant moderate RPS 24/7.", ProfileCategory.SPECIALIZED, "34m", 50),
        GgProfile("trickle", "Very low constant RPS to test idle connection timeout behaviour.", ProfileCategory.SPECIALIZED, "30m", 2),
        GgProfile("warm-up", "Very slow step ramp over 5 minutes for JIT and cache warming.", ProfileCategory.SPECIALIZED, "5m", 100),
    )

    val byCategory: Map<ProfileCategory, List<GgProfile>> = profiles.groupBy { it.category }

    fun byName(name: String): GgProfile? = profiles.firstOrNull { it.name == name }
}
