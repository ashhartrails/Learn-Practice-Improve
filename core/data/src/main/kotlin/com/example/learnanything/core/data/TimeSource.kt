package com.example.learnanything.core.data

import javax.inject.Inject

fun interface TimeSource {
    fun now(): Long
}

class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): Long = System.currentTimeMillis()
}
