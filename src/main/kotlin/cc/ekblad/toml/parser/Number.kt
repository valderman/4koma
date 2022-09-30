package cc.ekblad.toml.parser

import cc.ekblad.konbini.Parser
import cc.ekblad.konbini.map
import cc.ekblad.konbini.oneOf
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.toml.model.TomlValue

private const val digits = "[0-9](_?[0-9])*"

private val decimalInt: Parser<Long> = parser {
    val result = regex("[+-]?$digits")
    result.replace("_", "").toLongOrNull()
        ?: fail("Value '$result' out of range for 64-bit integer.")
}
private val hexadecimalInt = parser {
    val result = regex("0x[0-9a-fA-F](_?[0-9a-fA-F])*")
    result.replace("_", "").drop(2).toLongOrNull(16)
        ?: fail("Value '$result' out of range for 64-bit integer.")
}
private val octalInt = parser {
    val result = regex("0o[0-7](_?[0-7])*")
    result.replace("_", "").drop(2).toLongOrNull(8)
        ?: fail("Value '$result' out of range for 64-bit integer.")
}
private val binaryInt = parser {
    val result = regex("0b[01](_?[01])*")
    result.replace("_", "").drop(2).toLongOrNull(2)
        ?: fail("Value '$result' out of range for 64-bit integer.")
}

private val integer = oneOf(hexadecimalInt, binaryInt, octalInt, decimalInt).map { TomlValue.Integer(it) }

private val decimal = oneOf(
    regex("[+-]?$digits((\\.$digits)?[eE][+-]?$digits|\\.$digits)").map { it.replace("_", "").toDouble() },
    regex("[+-]?nan").map { Double.NaN },
    regex("[+]?inf").map { Double.POSITIVE_INFINITY },
    regex("-inf").map { Double.NEGATIVE_INFINITY },
).map { TomlValue.Double(it) }

internal val number = oneOf(decimal, integer)
