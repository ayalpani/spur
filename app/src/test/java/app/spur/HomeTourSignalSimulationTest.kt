package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTourSignalSimulationTest {
    @Test
    fun completeSignalSequencePreservesTheMeasuredDepartureAndReturnRoute() {
        val home = SpurCoordinate(52.0, 13.0)
        val settings = HomeAutoStartSettings(
            enabled = true,
            home = home,
            startPoint = home,
        )
        val departureSignals = listOf(
            signal(52.0001, at = 0L),
            signal(52.0004, at = 4_000L),
            signal(52.0008, at = 8_000L),
            signal(52.0012, at = 12_000L),
            signal(52.0013, at = 16_000L),
            signal(52.0014, at = 20_000L),
            signal(52.0015, at = 24_000L),
            signal(52.0016, at = 28_000L),
            signal(52.0017, at = 32_000L),
            signal(52.0018, at = 36_000L),
        )

        assertTrue(confirmedHomeDeparture(departureSignals, settings, candidateAt = 0L))
        val departure = departureLocations(
            locations = departureSignals,
            settings = settings,
            throughAt = departureSignals.last().recordedAt,
        )
        val route = automaticStartLocations(
            startPoint = home,
            measured = departure,
            exitAt = 0L,
        ).mapTo(mutableListOf()) { it.coordinate() }

        val returnSignals = listOf(
            signal(52.0014, at = 360_000L),
            signal(52.0008, at = 364_000L), // Inside 100 m: keep recording.
            signal(52.0006, at = 368_000L), // Detour inside the broad Home Zone.
            signal(52.0005, at = 372_000L),
            signal(52.0001, at = 376_000L),
            signal(52.00012, at = 380_000L),
            signal(52.00008, at = 384_000L),
            signal(52.00011, at = 388_000L),
            signal(52.00009, at = 392_000L),
            signal(52.0001, at = 396_000L),
        )
        val signalProcessor = AutomaticTourSignalProcessor<BufferedHomeLocation>(
            settings = settings,
            outsideSince = 1L,
            appendMeasured = { measured ->
                route += measured.coordinate()
                true
            },
            finishAtHome = { homeEndpoint, _ ->
                route += homeEndpoint
                true
            },
        )
        returnSignals.forEachIndexed { index, sample ->
            val result = signalProcessor.record(sample = sample, measured = sample)
            if (index < returnSignals.lastIndex) {
                assertFalse(result.finished)
            } else {
                assertTrue(result.finished)
            }
        }

        val expected = buildList {
            add(home)
            addAll(departureSignals.map { it.coordinate() })
            addAll(returnSignals.map { it.coordinate() })
            add(home)
        }
        assertEquals(expected, route)
    }

    @Test
    fun missingMeasuredStartBridgeDoesNotCreateAZeroTimeNinetyMeterEdge() {
        val home = SpurCoordinate(52.0, 13.0)
        val startPoint = SpurCoordinate(52.0, 13.000358)
        val settings = HomeAutoStartSettings(
            enabled = true,
            home = home,
            startPoint = startPoint,
        )
        val firstRealFix = signal(
            latitude = 52.0005315,
            longitude = 12.999366,
            at = 1_000_000L,
            accuracyMeters = 20.6f,
        )
        assertTrue(distanceMeters(home, startPoint) in 23.5..25.5)
        assertTrue(distanceMeters(startPoint, firstRealFix.coordinate()) in 89.0..91.0)

        val measured = departureLocations(
            locations = listOf(firstRealFix),
            settings = settings,
            throughAt = firstRealFix.recordedAt,
        )
        val assembled = automaticStartLocations(
            startPoint = startPoint,
            measured = measured,
            exitAt = firstRealFix.recordedAt,
        )
        val track = assembled.mapIndexed { index, point ->
            TrackPoint(index.toLong(), point.latitude, point.longitude, point.recordedAt)
        }

        assertEquals(listOf(firstRealFix), assembled)
        assertEquals(0.0, trackDistanceMeters(track), 0.001)
    }

    @Test
    fun enteringTheBroadHomeZoneNeverChangesAMeasuredCoordinate() {
        val home = SpurCoordinate(52.0, 13.0)
        val settings = HomeAutoStartSettings(enabled = true, home = home, startPoint = home)
        val measuredInsideBroadZone = signal(52.0006, at = 360_000L)

        var recorded: BufferedHomeLocation? = null
        val result = AutomaticTourSignalProcessor<BufferedHomeLocation>(
            settings = settings,
            outsideSince = 1L,
            appendMeasured = {
                recorded = it
                true
            },
            finishAtHome = { _, _ -> error("The tour must not finish here") },
        ).record(sample = measuredInsideBroadZone, measured = measuredInsideBroadZone)

        assertEquals(measuredInsideBroadZone, recorded)
        assertFalse(result.finished)
    }

    private fun signal(
        latitude: Double,
        longitude: Double = 13.0,
        at: Long,
        accuracyMeters: Float = 5f,
    ) = BufferedHomeLocation(
        latitude = latitude,
        longitude = longitude,
        recordedAt = at,
        accuracyMeters = accuracyMeters,
    )

    private fun BufferedHomeLocation.coordinate() = SpurCoordinate(latitude, longitude)

    private fun distanceMeters(from: SpurCoordinate, to: SpurCoordinate) =
        coordinateDistanceMeters(
            fromLatitude = from.latitude,
            fromLongitude = from.longitude,
            toLatitude = to.latitude,
            toLongitude = to.longitude,
        )
}
