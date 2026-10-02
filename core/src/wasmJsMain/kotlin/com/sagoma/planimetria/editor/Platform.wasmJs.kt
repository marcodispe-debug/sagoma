package com.sagoma.planimetria.editor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Nel browser c'è un solo thread: i salvataggi passano da quello normale. */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
