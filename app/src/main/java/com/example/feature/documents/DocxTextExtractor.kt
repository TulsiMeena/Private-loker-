package com.example.feature.documents

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Lightweight, completely offline DOCX text extractor.
 * Parses the internal word/document.xml structure using Android's built-in XmlPullParser.
 * Zero internet, zero cloud dependencies, zero external libraries.
 */
object DocxTextExtractor {

    fun extractText(docxBytes: ByteArray): String {
        return extractText(ByteArrayInputStream(docxBytes))
    }

    fun extractText(inputStream: InputStream): String {
        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        return parseDocumentXml(zip)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return "Unable to parse Word Document content: ${e.message}"
        }
        return "No text content found in Word Document."
    }

    private fun parseDocumentXml(xmlInputStream: InputStream): String {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xmlInputStream, "UTF-8")

        val result = StringBuilder()
        var eventType = parser.eventType

        var inParagraph = false
        var paragraphHasText = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p" -> {
                            inParagraph = true
                            paragraphHasText = false
                        }
                        "t" -> {
                            val text = parser.nextText()
                            if (text.isNotEmpty()) {
                                result.append(text)
                                paragraphHasText = true
                            }
                        }
                        "br", "cr" -> {
                            result.append("\n")
                        }
                        "tab" -> {
                            result.append("\t")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p") {
                        if (paragraphHasText) {
                            result.append("\n\n")
                        }
                        inParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }

        return result.toString().trim()
    }
}
