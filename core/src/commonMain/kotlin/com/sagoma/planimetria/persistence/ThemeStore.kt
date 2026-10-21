package com.sagoma.planimetria.persistence

/** Aspetto dell'app scelto dall'utente: chiaro, scuro o come il sistema (telefono, computer, browser). */
enum class ThemeChoice {
    Light,
    Dark,
    System,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.themeChoice(this)
}

/** Tema scelto, conservato tra un avvio e l'altro. Senza una scelta salvata l'app resta chiara. */
interface ThemeStore {
    fun load(): ThemeChoice
    fun save(choice: ThemeChoice)
}

/** Dove non si salva nulla (prove): sempre chiaro. */
object NoThemeStore : ThemeStore {
    override fun load() = ThemeChoice.Light
    override fun save(choice: ThemeChoice) = Unit
}
