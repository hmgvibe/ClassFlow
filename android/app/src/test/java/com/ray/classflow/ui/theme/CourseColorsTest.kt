package com.ray.classflow.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.ray.classflow.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseColorsTest {
    @Test
    fun duplicateColorsAreResolvedWithoutChangingCourseOrder() {
        val courses = listOf(
            Course(id = "course-b", name = "國文", colorKey = 1),
            Course(id = "course-a", name = "數學", colorKey = 1),
            Course(id = "course-c", name = "地理", colorKey = 2),
        )

        val resolved = resolveDistinctCourseColors(courses)
        val colors = resolved.map { courseColor(it.colorKey).toArgb() and 0x00FFFFFF }

        assertEquals(courses.map(Course::id), resolved.map(Course::id))
        assertEquals(colors.size, colors.distinct().size)
    }

    @Test
    fun randomColorDoesNotReuseAnExistingRenderedColor() {
        val usedKeys = listOf(0, 1, 2, 3, 4, 5)
        val nextKey = randomUnusedCourseColorKey(usedKeys)
        val usedColors = usedKeys.map { courseColor(it).toArgb() and 0x00FFFFFF }

        assertFalse((courseColor(nextKey).toArgb() and 0x00FFFFFF) in usedColors)
        assertTrue(nextKey in 0..32767)
    }
}
