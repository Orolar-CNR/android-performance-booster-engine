package com.example.systembooster.util

interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

class ConsoleLogger : Logger {
    override fun d(tag: String, message: String) {
        println("[$tag] [DEBUG] $message")
    }

    override fun i(tag: String, message: String) {
        println("[$tag] [INFO] $message")
    }

    override fun w(tag: String, message: String) {
        println("[$tag] [WARN] $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        System.err.println("[$tag] [ERROR] $message")
        throwable?.printStackTrace(System.err)
    }
}
