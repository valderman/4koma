package cc.ekblad.toml.parser

import cc.ekblad.konbini.chain1
import cc.ekblad.konbini.map
import cc.ekblad.konbini.oneOf
import cc.ekblad.konbini.regex

private val bareKey = regex("[A-Za-z0-9_-]+")
private val quotedKey = oneOf(basicString, literalString)
internal val key = chain1(oneOf(bareKey, quotedKey), regex("$ws\\.$ws")).map { it.terms }
