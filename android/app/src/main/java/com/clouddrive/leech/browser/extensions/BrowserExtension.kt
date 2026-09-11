package com.clouddrive.leech.browser.extensions

data class BrowserExtension(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val icon: String,
    var isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val jsCode: String = "",
    val cssCode: String = ""
)
