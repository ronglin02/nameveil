package com.ronglin.nameveil.core

const val DEFAULT_WORKERS = 4
const val MAX_WORKERS = 16

internal expect fun automaticWorkers(): Int
