package com.redrob.ui

import kotlinx.browser.document
import react.create
import react.dom.client.createRoot
import web.dom.Element

fun main() {
    val container = document.getElementById("root")!!.unsafeCast<Element>()
    createRoot(container).render(App.create())
}
