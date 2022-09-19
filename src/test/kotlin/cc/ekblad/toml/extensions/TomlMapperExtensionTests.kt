package cc.ekblad.toml.extensions

import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.atomicallyEncodeTo
import cc.ekblad.toml.decode
import cc.ekblad.toml.decodeWithDefaults
import cc.ekblad.toml.encodeTo
import cc.ekblad.toml.encodeToDocument
import cc.ekblad.toml.encodeToString
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TomlMapperExtensionTests : UnitTest {
    private data class TestObj(val xs: List<String>, val bool: Boolean)
    private data class TestDocument(val str: String, val obj: TestObj, val int: Int?)
    private val testDocument = TestDocument(
        str = "a string",
        obj = TestObj(
            xs = listOf("foo", "bar"),
            bool = true
        ),
        int = null
    )
    private val testDocumentDefaults = TestDocument(
        str = "",
        obj = TestObj(
            xs = emptyList(),
            bool = true
        ),
        int = null
    )
    private val mapper = tomlMapper { }

    @Test
    fun `encodeToDocument produces a valid TOML document`() {
        val document = mapper.encodeToDocument(mapOf("foo" to "bar"))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("bar")), document)
    }

    @Test
    fun `encodeToDocument throws serialization error on input which doesn't serialize to a valid document`() {
        val exception = assertFailsWith<TomlException.SerializationError.NotAMap> {
            mapper.encodeToDocument("hello")
        }
        assertEquals("hello", exception.sourceValue)
        assertEquals(TomlValue.String("hello"), exception.tomlValue)
    }

    @Test
    fun `encode and decode are inverses for appendable`() {
        val stringBuffer = StringBuffer()
        mapper.encodeTo(stringBuffer, testDocument)
        val string = stringBuffer.toString()

        assertEquals(
            testDocument,
            mapper.decode(string)
        )
        assertEquals(
            testDocument,
            mapper.decodeWithDefaults(testDocumentDefaults, string)
        )
    }

    @Test
    fun `encode and decode are inverses for string`() {
        val string = mapper.encodeToString(testDocument)

        assertEquals(
            testDocument,
            mapper.decode(string)
        )
        assertEquals(
            testDocument,
            mapper.decodeWithDefaults(testDocumentDefaults, string)
        )
    }

    @Test
    fun `encode and decode are inverses for stream`() {
        val outputStream = ByteArrayOutputStream()
        mapper.encodeTo(outputStream, testDocument)
        val bytes = outputStream.toByteArray()

        assertEquals(
            testDocument,
            mapper.decode(ByteArrayInputStream(bytes))
        )
        assertEquals(
            testDocument,
            mapper.decodeWithDefaults(testDocumentDefaults, ByteArrayInputStream(bytes))
        )
    }

    @Test
    fun `encode and decode are inverses for file`() {
        val path = createTempFile()
        try {
            mapper.encodeTo(path, testDocument)

            assertEquals(
                testDocument,
                mapper.decode(path)
            )
            assertEquals(
                testDocument,
                mapper.decodeWithDefaults(testDocumentDefaults, path)
            )
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun `atomicallyEncodeTo is the inverse of decode file`() {
        val tempDirectory = createTempDirectory()
        try {
            val tempFile = createTempFile(tempDirectory)
            mapper.atomicallyEncodeTo(tempFile, testDocument)
            assertEquals(testDocument, mapper.decode(tempFile))
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `atomicallyEncodeTo cleans up after itself on success`() {
        // Would be nice to have a test for the failure case as well,
        // but that's kind of hard to mock so... ¯\_(ツ)_/¯
        val tempDirectory = createTempDirectory()
        try {
            val tempFile = createTempFile(tempDirectory)
            mapper.atomicallyEncodeTo(tempFile, testDocument)
            val directoryEntries = tempDirectory.listDirectoryEntries()
            assertEquals(1, directoryEntries.size)
            assertEquals(tempFile.fileName, directoryEntries.single().fileName)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }
}
