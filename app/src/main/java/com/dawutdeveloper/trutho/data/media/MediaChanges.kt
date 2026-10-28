package com.dawutdeveloper.trutho.data.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object MediaChanges {
    val revision = MutableStateFlow(0L)
    fun notifyChanged() { revision.update { it + 1L } }
}
