package com.wasl.app.data

private val repeatedWhitespace = Regex("\\s+")

internal object LocalSearchQuery {
    fun normalize(value: String): String = value
        .trim()
        .replace(repeatedWhitespace, " ")

    fun toSqlLikePattern(value: String): String? {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return null

        val escaped = buildString(normalized.length) {
            normalized.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '%' -> append("\\%")
                    '_' -> append("\\_")
                    else -> append(character)
                }
            }
        }
        return "%$escaped%"
    }
}
