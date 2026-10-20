package com.sagoma.planimetria.persistence

/** Aspetto dell'app scelto dall'utente: chiaro, scuro o come il sistema (telefono, computer, browser). */
enum class ThemeChoice(val label: String) {
    Light("Chiaro"),
    Dark("Scuro"),
    System("Come il sistema"),
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
