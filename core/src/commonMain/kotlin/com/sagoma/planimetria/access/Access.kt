package com.sagoma.planimetria.access

/**
 * Diritti di utilizzo: l'unico punto che decide cosa è sbloccato per chi usa l'app. L'app è una sola per privati e
 * professionisti; l'interfaccia, il renderer e le esportazioni non guardano mai "chi è l'utente", chiedono qui.
 *
 * Non c'è ancora né un account vero né un sistema di pagamento: [Account] è un modello locale, così le regole si possono
 * scrivere e provare prima di collegarli. Quando arriveranno cambia da dove si legge l'account, non le domande.
 */

/** Uso privato o professionale. */
enum class Profile { Private, Professional }

/** Uscite che si possono acquistare una per una: la planimetria e la vista 3D sono uscite distinte. */
enum class OutputFormat { Plan, View3D }

/** Un'uscita acquistata: una stanza in un formato. Riscaricarla è gratis. */
data class OutputPurchase(val roomId: Long, val format: OutputFormat)

/** Chi sta usando l'app e cosa ha già acquistato. */
data class Account(
    val profile: Profile = Profile.Private,
    val purchases: Set<OutputPurchase> = emptySet(),
) {
    companion object {
        /** Senza account: privato, nessun acquisto. */
        val Guest = Account()
    }
}

/** Operazioni che possono richiedere un diritto. */
enum class Right {
    /** Inserire nuovi arredi dal catalogo. */
    InsertFurniture,
}

object Rights {
    /**
     * Watermark visibile sul progetto anche dentro l'app (pianta e vista 3D), così uno screenshot conserva il marchio.
     * Lo vede chi non ha un piano professionale; gli acquisti singoli sbloccano le uscite scaricate, non la consultazione.
     */
    fun showsWatermark(account: Account): Boolean = account.profile != Profile.Professional

    /**
     * L'uscita delle stanze [roomIds] nel formato [format] si può scaricare senza watermark: piano professionale, oppure
     * ogni stanza acquistata in quel formato. Senza stanze resta il watermark.
     */
    fun cleanOutput(account: Account, format: OutputFormat, roomIds: Collection<Long>): Boolean =
        account.profile == Profile.Professional ||
            (roomIds.isNotEmpty() && roomIds.all { OutputPurchase(it, format) in account.purchases })

    fun has(account: Account, right: Right): Boolean = when (right) {
        // Provvisorio: aperto a tutti finché non c'è il pagamento; per i privati diventerà un acquisto per singolo mobile.
        Right.InsertFurniture -> true
    }
}
