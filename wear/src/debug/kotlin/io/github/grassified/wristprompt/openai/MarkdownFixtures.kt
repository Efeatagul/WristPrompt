package io.github.grassified.wristprompt.openai

/** Synthetic text only. Never referenced by production sources. */
internal fun markdownFixture(scenario: DebugScenario): String? = when (scenario) {
    DebugScenario.MARKDOWN_BASIC, DebugScenario.STREAMING_MARKDOWN,
    DebugScenario.INTERRUPTED_MARKDOWN, DebugScenario.STOPPED_MARKDOWN,
    DebugScenario.TERMINAL_MARKDOWN_REPLACEMENT -> """
        # Watch notes

        A compact paragraph with **bold**, *italic* and `inline code`.

        > A short quotation, easy to read.

        ---

        7. First ordered item
        8. Second ordered item

        - First bullet
        - Second bullet

        İstanbul, é, 👨‍👩‍👧‍👦 🇹🇷. العربية مع `code`.
    """.trimIndent()
    DebugScenario.MARKDOWN_LONG -> (1..120).joinToString("\n\n") { "## Section $it\n\nA **readable** answer with *compact* native formatting. ".repeat(2) }
    DebugScenario.MARKDOWN_CODE -> "Code example:\n\n```kotlin\nfun greet() {\n    println(\"Hello watch\")\n}\n```\n\nContinue reading normally."
    DebugScenario.MARKDOWN_CODE_LONG_LINE -> "```text\n" + "  long line ".repeat(120) + "\nshort line\n```\n\nAfter the code."
    DebugScenario.MARKDOWN_OPEN_FENCE, DebugScenario.STREAMING_MARKDOWN_OPEN_SYNTAX -> "# Partial\n\n**unfinished emphasis\n\n```kotlin\n  still typing\n" + "  code\n".repeat(30)
    DebugScenario.MARKDOWN_TABLE -> "| Model | Speed | Quality |\n|---|---|---|\n| Luna | Fast | Good |\n| Sol | Medium | Better |"
    DebugScenario.MARKDOWN_TABLE_MALFORMED -> "|Name| |\n|---|---|\n|First|\n|Second|extra|preserved extra cell|"
    DebugScenario.MARKDOWN_TABLE_OVER_LIMIT -> "|Name|Value|\n|---|---|\n" + (1..110).joinToString("\n") { "|Row $it|Value $it|" }
    DebugScenario.MARKDOWN_LINK -> "# Links\n\n[Official label](https://example.invalid/path?q=İstanbul)\n\n[Deceptive example.invalid label](https://different.invalid/destination)"
    DebugScenario.MARKDOWN_BLOCKED_LINK -> "[Script](javascript:alert%281%29)\n\n[Intent](intent:example)\n\n[File](file:/private)\n\n[Data](data:text/plain,hello)"
    DebugScenario.MARKDOWN_RAW_HTML -> "<script>alert('inactive')</script>\n\n<img src='https://example.invalid/image'>\n\n<a onclick='inactive'>literal</a>\n\n<style>inactive</style>"
    DebugScenario.MARKDOWN_IMAGE -> "# Diagram\n\n![diagram alt text](https://example.invalid/never-fetched.png)\n\nOnly alt text appears."
    DebugScenario.MARKDOWN_DEEP_NESTING -> "> ".repeat(200) + "Deep quotation\n\n" + (0..40).joinToString("\n") { "  ".repeat(it) + "- Nested item $it" }
    DebugScenario.MARKDOWN_DELIMITER_STRESS, DebugScenario.MARKDOWN_MALFORMED -> "Malformed syntax\n\n" + "* _ [ ` < ".repeat(800)
    DebugScenario.MARKDOWN_LIST_LONG -> (1..200).joinToString("\n") { "$it. **Item $it** with wrapping text for compact watch reading." }
    DebugScenario.MARKDOWN_LONG_URL -> "[Long URL](https://example.invalid/" + "x".repeat(10000) + ")"
    else -> null
}
