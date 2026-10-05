package com.sagoma.planimetria.scanner

import android.content.Context
import com.google.ar.core.ArCoreApk

/** Esito del controllo "questo telefono può fare realtà aumentata?". */
enum class ArSupport {
    /** ARCore è installato e aggiornato: si può partire. */
    Ready,

    /** Il telefono è compatibile ma ARCore (Servizi Google Play per la RA) va installato o aggiornato: lo propone la sessione. */
    NeedsInstall,

    /** Il telefono non supporta ARCore. */
    Unsupported,

    /** Il sistema non ha ancora risposto: si richiede di nuovo dopo un attimo. */
    Checking,
}

object ArSupportCheck {
    /**
     * Prima verifica, senza aprire la fotocamera. Le risposte "in corso" ([ArSupport.Checking]) sono normali al primo avvio:
     * chi chiama riprova dopo qualche decimo di secondo.
     */
    fun check(context: Context): ArSupport {
        val availability = try {
            ArCoreApk.getInstance().checkAvailability(context)
        } catch (e: Exception) {
            return ArSupport.Unsupported
        }
        return when {
            availability.isTransient -> ArSupport.Checking
            availability == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupport.Ready
            availability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD ||
                availability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArSupport.NeedsInstall
            else -> ArSupport.Unsupported // UNSUPPORTED_DEVICE_NOT_CAPABLE e risposte sconosciute
        }
    }
}
