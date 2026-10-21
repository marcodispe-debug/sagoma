package com.sagoma.planimetria.persistence

/** Lingua scelta dall'utente: italiano, inglese, francese, spagnolo o quella del sistema (telefono, computer, browser). */
enum class LanguageChoice {
    Italian,
    English,
    French,
    Spanish,
    System,
    ;

    /** Nome da mostrare: le lingue col loro nome, "come il sistema" nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.languageChoice(this)
}

/** Lingua scelta, conservata tra un avvio e l'altro. Senza una scelta salvata vale quella del sistema. */
interface LanguageStore {
    fun load(): LanguageChoice
    fun save(choice: LanguageChoice)
}

/** Dove non si salva nulla (prove): sempre italiano. */
object NoLanguageStore : LanguageStore {
    override fun load() = LanguageChoice.Italian
    override fun save(choice: LanguageChoice) = Unit
}
