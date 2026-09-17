package com.example.feature.code.formatters

import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

data class XmlValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object XmlFormatter {

    /**
     * Validates XML structure securely with entity expansion and external DTDs disabled.
     */
    fun validate(rawXml: String): XmlValidationResult {
        val trimmed = rawXml.trim()
        if (trimmed.isEmpty()) return XmlValidationResult(isValid = false, errorMessage = "XML content is empty")

        return try {
            val dbf = DocumentBuilderFactory.newInstance()
            // Strict XXE mitigation
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val db = dbf.newDocumentBuilder()
            db.parse(InputSource(StringReader(trimmed)))
            XmlValidationResult(isValid = true)
        } catch (e: Exception) {
            XmlValidationResult(isValid = false, errorMessage = e.message ?: "Invalid XML structure")
        }
    }

    /**
     * Formats XML with consistent indentation.
     */
    fun prettyPrint(rawXml: String, indentSpaces: Int = 2): Result<String> {
        val trimmed = rawXml.trim()
        if (trimmed.isEmpty()) return Result.success("")

        return try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(InputSource(StringReader(trimmed)))

            val tf = TransformerFactory.newInstance()
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            val transformer = tf.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indentSpaces.toString())
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (trimmed.startsWith("<?xml")) "no" else "yes")

            val out = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(out))
            Result.success(out.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
