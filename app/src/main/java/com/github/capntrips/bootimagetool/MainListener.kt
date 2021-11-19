package com.github.capntrips.bootimagetool

internal class MainListener constructor(private val callback: () -> Unit) {
    fun resume() {
        callback.invoke()
    }
}
