package com.rodrigomachado.riff.core.encore

/**
 * Rips de jogos oficiais (Harmonix, Neversoft…): o usuário não os usa. Pelo estúdio no charter e pelo ícone do
 * setlist (vistos em respostas reais da API em 2026-10-12: rb1, rb2dlc, gh5, ghm).
 */
internal object OfficialCharts {
    private val STUDIOS = listOf(
        "harmonix",
        "neversoft",
        "redoctane",
        "vicarious visions",
        "freestylegames",
        "budcat",
        "beenox",
    )
    private val ICONS = setOf(
        "gh1", "gh2", "gh80s", "gh3", "ghot", "ghotd", "ghwt", "ghm", "gh5", "ghvh", "ghsh", "ghwor", "bh", "ghl",
        "rb1", "rb2", "rb3", "rb1dlc", "rb2dlc", "rb3dlc", "rbdlc", "rbacdc", "rbbeatles", "rbtb", "rbgd", "lrb", "rbn",
    )

    fun isOfficial(charter: String?, icon: String?): Boolean =
        icon?.lowercase() in ICONS || STUDIOS.any { charter?.lowercase()?.contains(it) == true }
}
