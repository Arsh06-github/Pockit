package com.pockit.app.parser

// Top-level result of parsing a full multi-line message.
data class MessageParseResult(
    val entries: List<ParsedEntry>,
    val commands: List<Command>,
    val needsAnswers: List<NeedsAnswer>,
    val typos: List<TypoError>,
)

fun parseMessage(message: String, ctx: ParserContext): MessageParseResult {
    val lines = message.lines().filter { it.isNotBlank() }

    // Detect group tag across the whole message (a single @tag applies to all lines)
    var messageGroupTag: String? = null
    for (line in lines) {
        val tagMatch = Regex("""@(\S+)""").find(line)
        if (tagMatch != null) {
            val tag = tagMatch.groupValues[1]
            val tagLower = tag.lowercase()
            if (tagLower != "search" && tagLower != "edit" && tagLower != "general") {
                val group = ctx.groups.firstOrNull {
                    it.name.lowercase() == tagLower && it.deletedAt == null
                }
                if (group != null) {
                    messageGroupTag = group.name
                }
            }
        }
    }

    val entries = mutableListOf<ParsedEntry>()
    val commands = mutableListOf<Command>()
    val needsAnswers = mutableListOf<NeedsAnswer>()
    val typos = mutableListOf<TypoError>()

    for (line in lines) {
        val trimmed = line.trim()
        val lower = trimmed.lowercase()

        when {
            lower.startsWith("@search", ignoreCase = true) ||
            lower.startsWith("@edit", ignoreCase = true) ||
            lower.startsWith("delete ") ||
            lower.startsWith("clear ") ||
            lower.startsWith("last ") && lower.contains("expenses") ||
            lower.startsWith("expenses between") ||
            lower.startsWith("settle up") ||
            isBalanceLine(lower) -> {
                val cmd = parseCommand(trimmed, ctx)
                if (cmd != null) commands.add(cmd)
                else typos.add(TypoError(trimmed, "Unrecognised command"))
            }
            else -> {
                when (val result = parseEntry(trimmed, ctx)) {
                    is ParsedEntry -> entries.add(result)
                    is NeedsAnswer -> needsAnswers.add(result)
                    is TypoError -> typos.add(result)
                }
            }
        }
    }

    return MessageParseResult(entries, commands, needsAnswers, typos)
}

private fun isBalanceLine(lower: String): Boolean =
    lower.contains(" owes ") || lower.startsWith("how much") ||
    lower.endsWith(" balance") || lower.endsWith(" owe")
