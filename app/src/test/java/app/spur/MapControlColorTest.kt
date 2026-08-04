package app.spur

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MapControlColorTest {
    @Test
    fun curatedThemesExposePrimarySecondaryAccentAndTrailColors() {
        assertEquals(3, SpurColorTheme.entries.size)
        assertEquals(
            MapControlColors(background = Color.Black, foreground = Color.White),
            SpurColorTheme.CLASSIC.mapControlColors,
        )
        assertEquals(
            TrailColors(
                fill = MapControlColor.BLUE.color,
                stroke = Color.Black.copy(alpha = TrailStrokeAlpha),
            ),
            SpurColorTheme.CLASSIC.trailColors,
        )
        assertEquals(MapControlColor.BLUE, SpurColorTheme.CLASSIC.accent)
        assertEquals(Color.Black, SpurColorTheme.CLASSIC.signalColor)
        assertEquals(MapControlColor.GREEN.color, SpurColorTheme.FOREST.signalColor)
    }

    @Test
    fun storedThemeFallsBackToClassic() {
        assertEquals(SpurColorTheme.FOREST, colorThemeFromStored("FOREST"))
        assertEquals(SpurColorTheme.CLASSIC, colorThemeFromStored("invalid"))
        assertEquals(SpurColorTheme.CLASSIC, colorThemeFromStored(null))
    }

    @Test
    fun paletteHasSixteenRainbowOrderedPersistentOptionsAndDefaultsToBlack() {
        assertEquals(16, MapControlColor.entries.size)
        assertEquals(
            listOf(
                MapControlColor.RED,
                MapControlColor.ORANGE,
                MapControlColor.AMBER,
                MapControlColor.YELLOW,
                MapControlColor.LIME,
                MapControlColor.GREEN,
                MapControlColor.TEAL,
                MapControlColor.CYAN,
                MapControlColor.BLUE,
                MapControlColor.INDIGO,
                MapControlColor.VIOLET,
                MapControlColor.PINK,
                MapControlColor.BROWN,
                MapControlColor.GRAY,
                MapControlColor.BLACK,
                MapControlColor.WHITE,
            ),
            MapControlColor.entries,
        )
        assertEquals(MapControlColor.BLUE, mapControlColorFromStored("BLUE"))
        assertEquals(MapControlColor.BLACK, mapControlColorFromStored("invalid"))
        assertEquals(MapControlColor.BLACK, mapControlColorFromStored(null))
        assertEquals(
            MapControlColor.YELLOW,
            mapControlColorFromStored("invalid", fallback = MapControlColor.YELLOW),
        )
    }

    @Test
    fun blueDefaultsToYellowForegroundWhileOtherColorsKeepReadableDefaults() {
        assertEquals(
            MapControlColor.YELLOW,
            defaultMapControlForeground(MapControlColor.BLUE),
        )
        assertEquals(
            MapControlColor.WHITE,
            defaultMapControlForeground(MapControlColor.GREEN),
        )
        assertEquals(
            MapControlColor.BLACK,
            defaultMapControlForeground(MapControlColor.YELLOW),
        )
    }

    @Test
    fun playerColorsInvertTheSelectedMapControlColors() {
        val selected = MapControlColors(
            background = Color(0xFF2563EB),
            foreground = Color.White,
        )

        assertEquals(Color.White, selected.inverted.background)
        assertEquals(Color(0xFF2563EB), selected.inverted.foreground)
    }

    @Test
    fun secondaryMapButtonInvertsTheSelectedColors() {
        val selected = MapControlColors(
            background = Color.Black,
            foreground = Color.White,
        )
        val secondary = secondaryMapControlStyle(selected)

        assertEquals(selected.inverted, secondary.colors)
        assertEquals(3.dp, secondary.border?.width)
        assertEquals(
            selected.inverted.foreground.copy(alpha = 0.25f),
            (secondary.border?.brush as SolidColor).value,
        )
    }

    @Test
    fun secondaryButtonUsesASelectedColorThatStaysVisibleOnWhite() {
        assertEquals(
            Color.Black,
            secondaryButtonContentColor(
                MapControlColors(background = Color.Black, foreground = Color.White),
            ),
        )
        assertEquals(
            Color.Black,
            secondaryButtonContentColor(
                MapControlColors(background = Color.White, foreground = Color.Black),
            ),
        )
        assertEquals(
            Ink,
            secondaryButtonContentColor(
                MapControlColors(background = Color.White, foreground = Color.Yellow),
            ),
        )
    }
}
