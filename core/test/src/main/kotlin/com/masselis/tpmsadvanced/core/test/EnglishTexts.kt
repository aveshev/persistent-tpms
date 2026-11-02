package com.masselis.tpmsadvanced.core.test

import com.masselis.tpmsadvanced.core.common.R
import com.masselis.tpmsadvanced.core.common.localized
import io.mockk.every
import io.mockk.mockkStatic
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Makes [localized] answer with the English texts, which a JVM test can't otherwise read: there's no
 * app there, the resources are read straight from core/common's strings.xml. To pair with
 * `unmockkAll()`.
 */
public fun mockLocalizedAsEnglish() {
    val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    val texts = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "core/common/src/main/res/values/strings.xml") }
        .first { it.exists() }
        .let { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        .getElementsByTagName("string")
        .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
        .associate { it.getAttribute("name") to it.textContent.unescaped() }
    mockkStatic("com.masselis.tpmsadvanced.core.common.AppLanguageKt")
    every { localized(any(), *anyVararg()) } answers {
        val text = texts.getValue(names.getValue(firstArg()))
        (args[1] as Array<*>).takeIf { it.isNotEmpty() }?.let { text.format(*it) } ?: text
    }
}

private fun String.unescaped() = removeSurrounding("\"")
    .replace("\\n", "\n")
    .replace("\\u00A0", " ")
    .replace(Regex("""\\(.)"""), "$1")
