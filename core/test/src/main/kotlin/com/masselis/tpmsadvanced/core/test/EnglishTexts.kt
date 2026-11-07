package com.masselis.tpmsadvanced.core.test

import com.masselis.tpmsadvanced.core.common.R
import com.masselis.tpmsadvanced.core.common.localized
import com.masselis.tpmsadvanced.core.common.localizedPlural
import io.mockk.every
import io.mockk.mockkStatic
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Makes [localized] and [localizedPlural] answer with the English texts, which a JVM test can't
 * otherwise read: there's no app there, the resources are read straight from core/common's
 * strings.xml. To pair with `unmockkAll()`.
 */
@Suppress("MaxLineLength")
public fun mockLocalizedAsEnglish() {
    val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    val pluralNames = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }
    val document = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "core/common/src/main/res/values/strings.xml") }
        .first { it.exists() }
        .let { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
    val texts = document
        .getElementsByTagName("string")
        .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
        .associate { it.getAttribute("name") to it.textContent.unescaped() }
    mockkStatic("com.masselis.tpmsadvanced.core.common.AppLanguageKt")
    every { localized(any(), *anyVararg()) } answers {
        val text = texts.getValue(names.getValue(firstArg()))
        (args[1] as Array<*>).takeIf { it.isNotEmpty() }?.let { text.format(*it) } ?: text
    }
    // English only tells one from other
    val plurals = document
        .getElementsByTagName("item")
        .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
        .groupBy { (it.parentNode as Element).getAttribute("name") }
        .mapValues { (_, items) -> items.associate { it.getAttribute("quantity") to it.textContent.unescaped() } }
    every { localizedPlural(any(), any(), *anyVararg()) } answers {
        val count = secondArg<Int>()
        val text = plurals.getValue(pluralNames.getValue(firstArg())).let { it[if (count == 1) "one" else "other"] ?: it.getValue("other") }
        (args[2] as Array<*>).takeIf { it.isNotEmpty() }?.let { text.format(*it) } ?: text
    }
}

private fun String.unescaped() = removeSurrounding("\"")
    .replace("\\n", "\n")
    .replace("\\u00A0", " ")
    .replace(Regex("""\\(.)"""), "$1")
