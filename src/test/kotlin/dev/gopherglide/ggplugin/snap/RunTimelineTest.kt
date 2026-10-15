package dev.gopherglide.ggplugin.snap

import com.google.gson.Gson
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RunTimelineTest {
    private val gson = Gson()

    private fun fixture(name: String): SnapModel =
        gson.fromJson(checkNotNull(javaClass.getResourceAsStream("/lcp/$name")).bufferedReader().readText(), SnapModel::class.java)

    private fun meta(json: String): SnapMeta = gson.fromJson(json, SnapMeta::class.java)

    @Test
    fun annotatedSnapshotParsesMarksAndBiasEvents() {
        val meta = fixture("snap-v130-annotated.snap").meta!!
        assertEquals(listOf("cache-warm", "canary-deploy"), meta.marks!!.map { it.label })
        assertEquals(listOf(10, 10, -5), meta.biasEvents!!.map { it.amount })
        assertEquals(listOf(10, 20, 15), meta.biasEvents!!.map { it.cumulative })
        assertEquals(15, meta.finalBias)
    }

    @Test
    fun olderSnapshotParsesWithNoAnnotations() {
        val meta = fixture("snap-v120-plain.snap").meta!!
        assertNull(meta.marks)
        assertNull(meta.biasEvents)
        assertNull(RunTimeline.from(meta))
    }

    @Test
    fun timelineFromTheRealSnapshotUsesItsWallClockLength() {
        val timeline = RunTimeline.from(fixture("snap-v130-annotated.snap").meta)!!
        assertEquals(14.04, timeline.durationSeconds, 0.05)
        assertEquals(listOf<RunAnnotation>(RunAnnotation.Mark("cache-warm", 6.013390542), RunAnnotation.Mark("canary-deploy", 11.036139417)), timeline.marks)
        assertEquals(3, timeline.biasChanges.size)
        assertEquals(15, timeline.finalBias)
    }

    @Test
    fun emptyOrNullAnnotationListsMeanNoTimeline() {
        assertNull(RunTimeline.from(meta("""{"marks":[],"bias_events":[]}""")))
        assertNull(RunTimeline.from(meta("""{"marks":null,"bias_events":null}""")))
        assertNull(RunTimeline.from(null))
    }

    @Test
    fun missingOrUnparseableTimesFallBackToTheLatestEvent() {
        val m = meta("""{"start_time":"","end_time":"nope","marks":[{"label":"a","elapsed_s":12.5}],"bias_events":[{"amount":5,"cumulative":5,"elapsed_s":20.0}]}""")
        assertEquals(20.0, RunTimeline.from(m)!!.durationSeconds, 0.0)
    }

    @Test
    fun anEndBeforeTheStartIsIgnored() {
        val m = meta("""{"start_time":"2026-10-13T10:00:30Z","end_time":"2026-10-13T10:00:00Z","marks":[{"label":"a","elapsed_s":7.0}]}""")
        assertEquals(7.0, RunTimeline.from(m)!!.durationSeconds, 0.0)
    }

    @Test
    fun anEventPastTheWallClockEndExtendsTheLength() {
        val m = meta("""{"start_time":"2026-10-13T10:00:00Z","end_time":"2026-10-13T10:00:10Z","marks":[{"label":"a","elapsed_s":12.0}]}""")
        assertEquals(12.0, RunTimeline.from(m)!!.durationSeconds, 0.0)
    }

    @Test
    fun noLengthAtAllMeansNoTimeline() {
        assertNull(RunTimeline.from(meta("""{"marks":[{"label":"a","elapsed_s":0.0}]}""")))
    }

    @Test
    fun zoneOffsetsAreHonoured() {
        val m = meta("""{"start_time":"2026-10-13T23:10:12.886111-03:00","end_time":"2026-10-14T02:10:42.886111Z","marks":[{"label":"a","elapsed_s":1.0}]}""")
        assertEquals(30.0, RunTimeline.from(m)!!.durationSeconds, 0.001)
    }

    @Test
    fun eventsComeOutInTimeOrder() {
        val m = meta("""{"marks":[{"label":"late","elapsed_s":9.0},{"label":"early","elapsed_s":2.0}]}""")
        assertEquals(listOf("early", "late"), RunTimeline.from(m)!!.marks.map { it.label })
    }

    @Test
    fun finalBiasFallsBackToTheLastCumulativeValue() {
        val m = meta("""{"bias_events":[{"amount":5,"cumulative":5,"elapsed_s":1.0},{"amount":-2,"cumulative":3,"elapsed_s":2.0}]}""")
        assertEquals(3, RunTimeline.from(m)!!.finalBias)
    }

    @Test
    fun summaryListsCountsFinalBiasAndLength() {
        val timeline = RunTimeline(150.0, List(2) { RunAnnotation.Mark("m", 1.0) }, List(3) { RunAnnotation.BiasChange(5, 5, 1.0) }, 15)
        assertEquals("2 marks · 3 bias changes · final bias +15 · 2:30 total", timeline.summary())
    }

    @Test
    fun summaryUsesSingularsAndSkipsWhatIsAbsent() {
        assertEquals("1 mark · 0:30 total", RunTimeline(30.0, listOf(RunAnnotation.Mark("m", 1.0)), emptyList(), null).summary())
        assertEquals(
            "1 bias change · final bias −5 · 1:05 total",
            RunTimeline(65.0, emptyList(), listOf(RunAnnotation.BiasChange(-5, -5, 1.0)), -5).summary()
        )
    }

    @Test
    fun timelineExistsForTheRealSnapshot() {
        assertNotNull(RunTimeline.from(fixture("snap-v130-annotated.snap").meta))
    }
}
