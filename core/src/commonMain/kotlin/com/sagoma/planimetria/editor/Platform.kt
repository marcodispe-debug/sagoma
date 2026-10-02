package com.sagoma.planimetria.editor

import kotlinx.coroutines.CoroutineDispatcher

/** Dove si scrivono i file: Dispatchers.IO su Android e JVM; nel browser non c'è, si usa quello normale. */
internal expect val ioDispatcher: CoroutineDispatcher
