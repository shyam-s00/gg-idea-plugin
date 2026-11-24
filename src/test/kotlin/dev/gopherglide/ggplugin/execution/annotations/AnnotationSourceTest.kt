package dev.gopherglide.ggplugin.execution.annotations

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationSourceTest {
    @Test
    fun namedSourcesMapToTheirBadges() {
        assertEquals(AnnotationSource.AUTOPILOT, AnnotationSource.fromWire("autopilot"))
        assertEquals(AnnotationSource.AGENT, AnnotationSource.fromWire("agent"))
        assertEquals(AnnotationSource.USER, AnnotationSource.fromWire("user"))
        assertEquals("⚡", AnnotationSource.AUTOPILOT.badge)
        assertEquals("🤖", AnnotationSource.AGENT.badge)
        assertEquals("👤", AnnotationSource.USER.badge)
    }

    @Test
    fun aMissingOrUnknownSourceIsTheUser() {
        assertEquals(AnnotationSource.USER, AnnotationSource.fromWire(null))
        assertEquals(AnnotationSource.USER, AnnotationSource.fromWire(""))
        assertEquals(AnnotationSource.USER, AnnotationSource.fromWire("someone-new"))
    }

    @Test
    fun annotationsDefaultToTheUser() {
        assertEquals(AnnotationSource.USER, RunAnnotation.Mark("x", 1.0).source)
        assertEquals(AnnotationSource.USER, RunAnnotation.BiasChange(5, 5, 1.0).source)
        assertEquals(AnnotationSource.USER, RunAnnotation.HoldChange(10, 1.0).source)
    }
}
