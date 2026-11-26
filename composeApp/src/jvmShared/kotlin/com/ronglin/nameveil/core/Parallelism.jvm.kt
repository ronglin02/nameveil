package com.ronglin.nameveil.core

internal actual fun automaticWorkers(): Int =
    Runtime.getRuntime().availableProcessors().coerceIn(1, DEFAULT_WORKERS)
