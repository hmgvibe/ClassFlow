package com.ray.classflow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ray.classflow.model.Course
import kotlin.random.Random

val CoursePalette = listOf(
    Color(0xFF00796B),
    Color(0xFF1565C0),
    Color(0xFF7B1FA2),
    Color(0xFFD05A00),
    Color(0xFF2E7D32),
    Color(0xFFC2185B),
    Color(0xFF00838F),
    Color(0xFFC62828),
    Color(0xFF3949AB),
    Color(0xFFA66A00),
    Color(0xFF6B7D12),
    Color(0xFF6D4C41),
    Color(0xFF5E35B1),
    Color(0xFFAD1457),
    Color(0xFF0277BD),
    Color(0xFF558B2F),
    Color(0xFFEF6C00),
    Color(0xFF00897B),
    Color(0xFF9E4A32),
    Color(0xFF546E7A),
    Color(0xFF6A1B9A),
    Color(0xFF00695C),
    Color(0xFF283593),
    Color(0xFFB71C1C),
)

private const val MaxCourseColorKey = 0x7FFF

fun courseColor(colorKey: Int): Color {
    if (colorKey in CoursePalette.indices) {
        return CoursePalette[colorKey]
    }

    val packed = colorKey and MaxCourseColorKey
    val red = (((packed shr 10) and 0x1F) * 255 + 15) / 31
    val green = (((packed shr 5) and 0x1F) * 255 + 15) / 31
    val blue = ((packed and 0x1F) * 255 + 15) / 31
    val rgb = (red shl 16) or (green shl 8) or blue
    return Color(0xFF000000L or rgb.toLong())
}

fun randomUnusedCourseColorKey(usedColorKeys: Collection<Int>): Int {
    val usedColors = usedColorKeys.mapTo(mutableSetOf()) { courseColor(it).toArgb() and 0x00FFFFFF }
    val availableKeys = CoursePalette.indices.filter { key ->
        (courseColor(key).toArgb() and 0x00FFFFFF) !in usedColors
    }
    if (availableKeys.isNotEmpty()) {
        return availableKeys.random()
    }

    repeat(720) {
        val candidateKey = rgb555Key(Color.hsv(
            hue = Random.nextInt(360).toFloat(),
            saturation = 0.68f,
            value = 0.76f,
        ))
        val candidateColor = courseColor(candidateKey).toArgb() and 0x00FFFFFF
        if (candidateKey >= CoursePalette.size && candidateColor !in usedColors) {
            return candidateKey
        }
    }

    var candidateKey = CoursePalette.size
    while ((courseColor(candidateKey).toArgb() and 0x00FFFFFF) in usedColors) {
        candidateKey++
    }
    return candidateKey
}

fun resolveDistinctCourseColors(courses: List<Course>): List<Course> {
    val usedColors = mutableSetOf<Int>()
    val resolvedKeys = mutableMapOf<String, Int>()

    courses.sortedBy(Course::id).forEach { course ->
        val preferredColor = courseColor(course.colorKey).toArgb() and 0x00FFFFFF
        val resolvedKey = if (usedColors.add(preferredColor)) {
            course.colorKey
        } else {
            deterministicUnusedColorKey(course.id, usedColors).also(usedColors::add)
        }
        resolvedKeys[course.id] = resolvedKey
    }

    return courses.map { course ->
        val resolvedKey = resolvedKeys.getValue(course.id)
        if (course.colorKey == resolvedKey) course else course.copy(colorKey = resolvedKey)
    }
}

private fun deterministicUnusedColorKey(courseId: String, usedColors: Set<Int>): Int {
    val startIndex = courseId.hashCode().mod(CoursePalette.size)
    repeat(CoursePalette.size) { offset ->
        val candidateKey = (startIndex + offset) % CoursePalette.size
        val candidateColor = courseColor(candidateKey).toArgb() and 0x00FFFFFF
        if (candidateColor !in usedColors) {
            return candidateKey
        }
    }

    var seed = courseId.hashCode()
    while (true) {
        seed = seed * 1_664_525 + 1_013_904_223
        val candidateKey = CoursePalette.size + seed.mod(MaxCourseColorKey - CoursePalette.size + 1)
        val candidateColor = courseColor(candidateKey).toArgb() and 0x00FFFFFF
        if (candidateColor !in usedColors) {
            return candidateKey
        }
    }
}

private fun rgb555Key(color: Color): Int {
    val rgb = color.toArgb()
    val red = ((rgb shr 16) and 0xFF) * 31 / 255
    val green = ((rgb shr 8) and 0xFF) * 31 / 255
    val blue = (rgb and 0xFF) * 31 / 255
    return (red shl 10) or (green shl 5) or blue
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF2E7),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    secondaryContainer = Color(0xFFCCE8E3),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFFDEE5E3),
    onSurface = Color(0xFF191C1B),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81D5CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFF9DF2E7),
    secondary = Color(0xFFB0CCC7),
    secondaryContainer = Color(0xFF324B47),
    background = Color(0xFF101413),
    surface = Color(0xFF101413),
    surfaceVariant = Color(0xFF3F4947),
    onSurface = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ClassFlowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
