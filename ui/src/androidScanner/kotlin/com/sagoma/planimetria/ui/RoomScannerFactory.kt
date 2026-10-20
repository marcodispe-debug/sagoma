package com.sagoma.planimetria.ui

import androidx.activity.ComponentActivity

/** Build con lo scanner (`-Psagoma.scanner=true`): scansione con ARCore. */
internal fun createRoomScanner(activity: ComponentActivity): RoomScanner? = AndroidRoomScanner(activity)
