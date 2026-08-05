package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTourSignalSimulationTest {
    @Test
    fun motionMovesTheArmedServiceToHighAccuracyConfirmation() {
        val confirming = HomeDepartureTrackingMode.ARMED.afterMotion()

        assertEquals(HomeDepartureTrackingMode.CONFIRMING, confirming)
        assertEquals(
            HomeDepartureLocationCapture.HIGH_ACCURACY,
            locationCaptureFor(confirming),
        )
        assertEquals(
            HomeDepartureLocationCapture.NONE,
            locationCaptureFor(HomeDepartureTrackingMode.ARMED),
        )
    }

    @Test
    fun idleAutomationRearmsAfterAFalseCandidateOrCompletedTour() {
        assertEquals(
            HomeDepartureTrackingMode.ARMED,
            idleHomeDepartureMode(automationEnabled = true),
        )
        assertEquals(
            HomeDepartureTrackingMode.STOPPED,
            idleHomeDepartureMode(automationEnabled = false),
        )
    }

    @Test
    fun committedAutomaticCompletionSurvivesSessionReplacement() {
        assertTrue(
            shouldTransitionAfterAutomaticCompletion(
                completedTourId = 7L,
                activeTourId = 7L,
            ),
        )
        assertFalse(
            shouldTransitionAfterAutomaticCompletion(
                completedTourId = 7L,
                activeTourId = 8L,
            ),
        )
        assertFalse(
            shouldTransitionAfterAutomaticCompletion(
                completedTourId = 7L,
                activeTourId = null,
            ),
        )
    }

    @Test
    fun serviceRestartRestoresActiveConfirmationOrArmedState() {
        assertEquals(
            HomeDepartureTrackingMode.ACTIVE,
            restoredHomeDepartureMode(
                hasActiveTour = true,
                automationEnabled = true,
                candidateAt = null,
                now = 60_000L,
            ),
        )
        assertEquals(
            HomeDepartureTrackingMode.CONFIRMING,
            restoredHomeDepartureMode(
                hasActiveTour = false,
                automationEnabled = true,
                candidateAt = 1_000L,
                now = 60_000L,
            ),
        )
        assertEquals(
            HomeDepartureTrackingMode.ARMED,
            restoredHomeDepartureMode(
                hasActiveTour = false,
                automationEnabled = true,
                candidateAt = 1_000L,
                now = DepartureConfirmationTimeoutMillis + 1_000L,
            ),
        )
        assertEquals(
            HomeDepartureTrackingMode.ARMED,
            restoredHomeDepartureMode(
                hasActiveTour = false,
                automationEnabled = true,
                candidateAt = null,
                now = 60_000L,
            ),
        )
        assertEquals(
            HomeDepartureTrackingMode.STOPPED,
            restoredHomeDepartureMode(
                hasActiveTour = false,
                automationEnabled = false,
                candidateAt = null,
                now = 60_000L,
            ),
        )
    }

    @Test
    fun lateOutOfOrderPreRollFlushRebuildsTheMeasuredStartContinuously() {
        val startPoint = SpurCoordinate(52.0, 13.0)
        val settings = HomeAutoStartSettings(
            enabled = true,
            home = startPoint,
            startPoint = startPoint,
        )
        val bridge = signal(52.00004, at = 1_000L)
        val second = signal(52.00016, at = 2_000L)
        val third = signal(52.00028, at = 3_000L)
        val confirmed = signal(52.00040, at = 4_000L)
        val firstAssembly = automaticStartLocations(
            startPoint = startPoint,
            measured = departureLocations(
                locations = listOf(bridge, confirmed),
                settings = settings,
                candidateAt = 500L,
                throughAt = confirmed.recordedAt,
            ),
            exitAt = 500L,
        )

        val persisted = mergeBufferedHomeLocations(
            existing = listOf(confirmed, bridge),
            incoming = listOf(third, second),
            now = confirmed.recordedAt,
        )
        val rebuilt = automaticStartLocations(
            startPoint = startPoint,
            measured = departureLocations(
                locations = persisted,
                settings = settings,
                candidateAt = 500L,
                throughAt = confirmed.recordedAt,
            ),
            exitAt = 500L,
        )

        assertEquals(listOf(500L, 1_000L, 4_000L), firstAssembly.map { it.recordedAt })
        assertEquals(
            listOf(500L, 1_000L, 2_000L, 3_000L, 4_000L),
            rebuilt.map { it.recordedAt },
        )
        val rebuiltTrack = rebuilt.mapIndexed { index, point ->
            TrackPoint(index.toLong(), point.latitude, point.longitude, point.recordedAt)
        }
        assertTrue(
            rebuiltTrack.zipWithNext().all { (from, to) ->
                distanceMeters(
                    SpurCoordinate(from.latitude, from.longitude),
                    SpurCoordinate(to.latitude, to.longitude),
                ) < 20.0
            },
        )
    }

    @Test
    fun automaticStartDuplicateIndexKeepsTheExistingTimeAndDistanceBoundaries() {
        val index = AutomaticStartDuplicateIndex(
            listOf(TrackPoint(1L, 52.0, 13.0, 2_000L)),
        )

        assertTrue(index.contains(signal(52.00001, at = 3_000L)))
        assertFalse(index.contains(signal(52.00001, at = 3_001L)))
        assertFalse(index.contains(signal(52.00003, at = 2_000L)))

        index.add(TrackPoint(2L, 52.00003, 13.0, 2_000L))
        assertTrue(index.contains(signal(52.00003, at = 2_000L)))
    }

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
            candidateAt = 0L,
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
            candidateAt = firstRealFix.recordedAt,
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
    fun departureDiagnosticsUseOnlyANonCoordinateDistanceBucket() {
        assertEquals("under_10m", homeDepartureDistanceBucket(9.9))
        assertEquals("10_24m", homeDepartureDistanceBucket(24.9))
        assertEquals("25_49m", homeDepartureDistanceBucket(49.9))
        assertEquals("50_99m", homeDepartureDistanceBucket(99.9))
        assertEquals("100m_or_more", homeDepartureDistanceBucket(100.0))
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
        haversineDistanceMeters(
            fromLatitude = from.latitude,
            fromLongitude = from.longitude,
            toLatitude = to.latitude,
            toLongitude = to.longitude,
        )
}
