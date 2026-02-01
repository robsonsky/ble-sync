package com.hailie.domain.policy

import com.hailie.domain.PageSize

/**
 * Outcome of the last page cycle to inform sizing decisions.
 */
enum class PageOutcome {
    /**
     * Page was read/delivered/acked as planned (no fragmentation, no errors).
     */
    Stable,

    /**
     * Page completed but with soft warnings (e.g., minor retries) — grow more cautiously.
     */
    MostlyStable,

    /**
     * Read failed due to transient conditions (timeouts, temporary GATT).
     */
    TransientFailure,

    /**
     * Permanent failure or repeated failures (policy can shrink aggressively).
     */
    HardFailure,
}

/**
 * Contract for computing the next page size.
 */
fun interface PageSizingPolicy {
    fun next(
        current: PageSize,
        outcome: PageOutcome,
    ): PageSize
}

/**
 * Default behavior:
 * - Stable: grow by [growStep], clamp to [maxPage].
 * - MostlyStable: grow by [growStep / 2], clamp to [maxPage].
 * - TransientFailure: shrink by [shrinkStep], clamp to [minPage].
 * - HardFailure: shrink by [shrinkStep * 2], clamp to [minPage].
 */
class AdaptivePageSizingPolicy(
    private val minPage: Int = 20,
    private val maxPage: Int = 200,
    private val growStep: Int = 20,
    private val shrinkStep: Int = 20,
) : PageSizingPolicy {
    init {
        require(minPage > 0) { "minPage must be > 0" }
        require(maxPage >= minPage) { "maxPage must be >= minPage" }
        require(growStep > 0) { "growStep must be > 0" }
        require(shrinkStep > 0) { "shrinkStep must be > 0" }
    }

    override fun next(
        current: PageSize,
        outcome: PageOutcome,
    ): PageSize {
        val cur = current.value
        val next =
            when (outcome) {
                PageOutcome.Stable -> (cur + growStep).coerceAtMost(maxPage)
                PageOutcome.MostlyStable -> (cur + (growStep / 2).coerceAtLeast(1)).coerceAtMost(maxPage)
                PageOutcome.TransientFailure -> (cur - shrinkStep).coerceAtLeast(minPage)
                PageOutcome.HardFailure -> (cur - (shrinkStep * 2)).coerceAtLeast(minPage)
            }
        return PageSize(next)
    }
}
