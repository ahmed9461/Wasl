package com.wasl.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

/** Host-side regression checks: run with testDebugUnitTest, without an emulator. */
class LauncherIconResourcesTest {
    private val res = File("src/main/res")
    private val android = "http://schemas.android.com/apk/res/android"

    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(res, path)).documentElement

    private fun Element.attribute(name: String) = getAttributeNS(android, name)
    private fun Element.elements(tag: String): List<Element> = getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun everyAdaptiveVariantUsesTheSameValidVectorAndFullBleedBackground() {
        for (version in listOf(26, 33)) {
            for (name in listOf("ic_launcher", "ic_launcher_round")) {
                val icon = xml("mipmap-anydpi-v$version/$name.xml")
                assertEquals("@drawable/wasl_launcher_art", icon.elements("foreground").single().attribute("drawable"))
                assertEquals("@drawable/wasl_launcher_background", icon.elements("background").single().attribute("drawable"))
                if (version == 33) {
                    assertEquals("@drawable/ic_launcher_foreground", icon.elements("monochrome").single().attribute("drawable"))
                }
            }
        }
        assertEquals("vector", xml("drawable/wasl_launcher_art.xml").tagName)
        assertEquals("rectangle", xml("drawable/wasl_launcher_background.xml").attribute("shape"))
        assertFalse(File(res, "drawable-nodpi/wasl_launcher_art.png").exists(), "Do not restore the undecodable PNG.")
        assertTrue(xml("drawable/ic_launcher_legacy.xml").elements("bitmap").isEmpty())
    }

    @Test
    fun themedAndNotificationSilhouettesMatchTheActualWordmark() {
        val color = xml("drawable/wasl_launcher_art.xml")
        val mono = xml("drawable/ic_launcher_foreground.xml")
        for (name in listOf("width", "height", "viewportWidth", "viewportHeight")) {
            assertEquals(color.attribute(name), mono.attribute(name))
        }
        for (name in listOf("scaleX", "scaleY", "translateX", "translateY")) {
            assertEquals(color.elements("group").single().attribute(name), mono.elements("group").single().attribute(name))
        }
        val coloredPaths = color.elements("path")
        val monoPaths = mono.elements("path")
        assertEquals(coloredPaths.size, monoPaths.size)
        coloredPaths.zip(monoPaths).forEach { (a, b) ->
            for (name in listOf("pathData", "fillType", "strokeWidth", "strokeLineCap")) {
                assertEquals(a.attribute(name), b.attribute(name), name)
            }
            assertTrue(b.attribute("fillColor") in setOf("#FFFFFFFF", "@android:color/transparent"))
            assertTrue(b.attribute("strokeColor") in setOf("", "#FFFFFFFF"))
        }
    }

    @Test
    fun theCompleteArtworkStaysInsideThe66DpSafeCircle() {
        val vector = xml("drawable/wasl_launcher_art.xml")
        assertEquals("108", vector.attribute("viewportWidth"))
        assertEquals("108", vector.attribute("viewportHeight"))
        val group = vector.elements("group").single()
        val scaleX = group.attribute("scaleX").toDouble()
        val scaleY = group.attribute("scaleY").toDouble()
        val tx = group.attribute("translateX").toDouble()
        val ty = group.attribute("translateY").toDouble()
        vector.elements("path").forEach { path ->
            val stroke = (path.attribute("strokeWidth").toDoubleOrNull() ?: 0.0) * maxOf(scaleX, scaleY) / 2
            val tokens = Regex("[MCLZ]|-?\\d+(?:\\.\\d+)?").findAll(path.attribute("pathData")).map { it.value }.toList()
            var i = 0
            var x = 0.0
            var y = 0.0
            var startX = 0.0
            var startY = 0.0
            fun checkPoint(px: Double, py: Double) {
                assertTrue(hypot(px * scaleX + tx - 54, py * scaleY + ty - 54) + stroke <= 33,
                    "Artwork is clipped by the adaptive icon safe circle: $px,$py")
            }
            while (i < tokens.size) {
                when (val command = tokens[i++]) {
                    "M", "L" -> {
                        x = tokens[i++].toDouble(); y = tokens[i++].toDouble()
                        if (command == "M") { startX = x; startY = y }
                        checkPoint(x, y)
                    }
                    "C" -> {
                        val x1 = tokens[i++].toDouble(); val y1 = tokens[i++].toDouble()
                        val x2 = tokens[i++].toDouble(); val y2 = tokens[i++].toDouble()
                        val x3 = tokens[i++].toDouble(); val y3 = tokens[i++].toDouble()
                        for (step in 0..100) {
                            val t = step / 100.0; val u = 1 - t
                            checkPoint(u*u*u*x + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x3,
                                u*u*u*y + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y3)
                        }
                        x = x3; y = y3
                    }
                    "Z" -> { x = startX; y = startY }
                    else -> error("Unsupported path command: $command")
                }
            }
        }
    }
}
