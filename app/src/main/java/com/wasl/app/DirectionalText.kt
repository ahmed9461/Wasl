package com.wasl.app

private const val LTR_ISOLATE = "\u2066"
private const val POP_DIRECTIONAL_ISOLATE = "\u2069"

internal fun ltrIsolate(value: String): String {
    if (value.startsWith(LTR_ISOLATE) && value.endsWith(POP_DIRECTIONAL_ISOLATE)) {
        return value
    }
    return "$LTR_ISOLATE$value$POP_DIRECTIONAL_ISOLATE"
}
