package cc.ekblad.toml

interface StringTest : RandomTest {
    val escapeCodeSamples: List<Pair<String, String>>
        get() = listOf(
            "\\b" to "\b",
            "\\f" to "\u000C",
            "\\n" to "\n",
            "\\r" to "\r",
            "\\t" to "\t",
            "\\\"" to "\"",
            "\\\\" to "\\",
            "\\u00e5" to "å",
            "\\U0001f63f" to "😿"
        )
}
