package cc.ekblad.toml

data class TomlException(
    val errorDescription: String,
    val line: Int,
    override val cause: Throwable?
) : RuntimeException() {
    constructor(errorDescription: String, line: Int) : this(errorDescription, line, null)

    override val message: String =
        "toml parse error, on line $line: $errorDescription"
}
