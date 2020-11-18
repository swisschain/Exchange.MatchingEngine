package com.swisschain.utils.logging

internal data class Error(val type: String, val sender: String, val message: String) {
    override fun toString(): String {
        return "Error(type='$type', sender='$sender', message='$message')"
    }
}