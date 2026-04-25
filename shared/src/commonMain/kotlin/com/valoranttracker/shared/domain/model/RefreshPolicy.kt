package com.valoranttracker.shared.domain.model

enum class RefreshPolicy {
    SKIP_FRESH,
    SERVE_BACKGROUND,
    SERVE_FORCE_REFRESH,
    TIGHTEN_15MIN,
    LOOSEN_6H,
    CIRCUIT_BREAKER_PAUSE,
}
