package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal data class CachedRoadTraversals(
    val fingerprint: RoadHistoryFingerprint,
    val completedRoads: Map<String, CompletedRoad>,
)

internal class RoadTraversalStore(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        RoadTraversalDatabaseName,
        null,
        RoadTraversalDatabaseVersion,
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE road_traversal_cells (
                cache_key TEXT PRIMARY KEY,
                maximum_point_id INTEGER NOT NULL,
                point_count INTEGER NOT NULL,
                signature INTEGER NOT NULL,
                completed_roads BLOB NOT NULL
            )
            """.trimIndent(),
        )
        createRoadProgressState(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 2 && newVersion >= 3) {
            createRoadProgressState(db)
            return
        }
        db.execSQL("DROP TABLE IF EXISTS road_traversal_cells")
        db.execSQL("DROP TABLE IF EXISTS road_progress_state")
        onCreate(db)
    }

    private fun createRoadProgressState(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS road_progress_state (
                id INTEGER PRIMARY KEY,
                maximum_point_id INTEGER NOT NULL,
                point_count INTEGER NOT NULL,
                signature INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    @Synchronized
    fun traversals(cacheKey: String): CachedRoadTraversals? =
        readableDatabase.query(
            "road_traversal_cells",
            arrayOf("maximum_point_id", "point_count", "signature", "completed_roads"),
            "cache_key = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val completedRoads = decodeCompletedRoads(cursor.getBlob(3))
                ?: return@use null
            CachedRoadTraversals(
                fingerprint = RoadHistoryFingerprint(
                    maximumPointId = cursor.getLong(0),
                    pointCount = cursor.getLong(1),
                    signature = cursor.getLong(2),
                ),
                completedRoads = completedRoads,
            )
        }

    @Synchronized
    fun replace(cacheKey: String, traversals: CachedRoadTraversals) {
        writableDatabase.insertWithOnConflict(
            "road_traversal_cells",
            null,
            ContentValues().apply {
                put("cache_key", cacheKey)
                put("maximum_point_id", traversals.fingerprint.maximumPointId)
                put("point_count", traversals.fingerprint.pointCount)
                put("signature", traversals.fingerprint.signature)
                put("completed_roads", encodeCompletedRoads(traversals.completedRoads.values))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun historyBaseline(): RoadHistoryFingerprint? =
        readableDatabase.query(
            "road_progress_state",
            arrayOf("maximum_point_id", "point_count", "signature"),
            "id = ?",
            arrayOf(RoadProgressStateId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            RoadHistoryFingerprint(
                maximumPointId = cursor.getLong(0),
                pointCount = cursor.getLong(1),
                signature = cursor.getLong(2),
            )
        }

    @Synchronized
    fun replaceHistoryBaseline(fingerprint: RoadHistoryFingerprint) {
        writableDatabase.insertWithOnConflict(
            "road_progress_state",
            null,
            ContentValues().apply {
                put("id", RoadProgressStateId)
                put("maximum_point_id", fingerprint.maximumPointId)
                put("point_count", fingerprint.pointCount)
                put("signature", fingerprint.signature)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun retain(fingerprint: RoadHistoryFingerprint) {
        writableDatabase.delete(
            "road_traversal_cells",
            "maximum_point_id != ? OR point_count != ? OR signature != ?",
            arrayOf(
                fingerprint.maximumPointId.toString(),
                fingerprint.pointCount.toString(),
                fingerprint.signature.toString(),
            ),
        )
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("road_traversal_cells", null, null)
    }

    @Synchronized
    fun overviewSegments(): List<List<SpurCoordinate>> =
        readableDatabase.query(
            "road_traversal_cells",
            arrayOf("cache_key", "completed_roads"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            mergeOverviewRoadCoverageSegments(
                buildList {
                    while (cursor.moveToNext()) {
                        val zoom = roadTraversalCacheZoom(cursor.getString(0)) ?: continue
                        decodeCompletedRoads(cursor.getBlob(1))?.values?.forEach { completed ->
                            add(zoom to completed.road.points)
                        }
                    }
                },
            )
        }
}

internal fun preservesRoadProgress(
    cached: RoadHistoryFingerprint,
    current: RoadHistoryFingerprint,
    added: RoadHistoryFingerprint,
): Boolean = cached == current || cached + added == current

internal fun deleteLegacyRoadCoverageCache(context: Context) {
    context.applicationContext.deleteDatabase(LegacyRoadCoverageDatabaseName)
}

internal fun encodeCompletedRoads(completedRoads: Collection<CompletedRoad>): ByteArray =
    ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(RoadTraversalEncodingVersion)
            output.writeInt(completedRoads.size)
            completedRoads.sortedBy { it.road.key }.forEach { completedRoad ->
                output.writeSizedString(completedRoad.road.key)
                output.writeSizedString(completedRoad.road.grade)
                output.writeInt(completedRoad.road.kind.ordinal)
                output.writeInt(completedRoad.count)
                output.writeInt(completedRoad.road.points.size)
                completedRoad.road.points.forEach { point ->
                    output.writeDouble(point.latitude)
                    output.writeDouble(point.longitude)
                }
            }
        }
        bytes.toByteArray()
    }

internal fun decodeCompletedRoads(bytes: ByteArray): Map<String, CompletedRoad>? =
    runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == RoadTraversalEncodingVersion)
            val roadCount = input.readInt()
            require(roadCount in 0..RoadTraversalMaximumRoads)
            buildMap(roadCount) {
                repeat(roadCount) {
                    val key = input.readSizedString()
                    val grade = input.readSizedString()
                    val kind = RoadKind.entries.getOrNull(input.readInt())
                        ?: error("Unknown road kind")
                    val count = input.readInt()
                    require(count in 1..RoadTraversalMaximumCount)
                    val pointCount = input.readInt()
                    require(pointCount in 2..RoadTraversalMaximumPointsPerRoad)
                    val points = buildList(pointCount) {
                        repeat(pointCount) {
                            val latitude = input.readDouble()
                            val longitude = input.readDouble()
                            require(latitude.isFinite() && latitude in -90.0..90.0)
                            require(longitude.isFinite() && longitude in -180.0..180.0)
                            add(SpurCoordinate(latitude, longitude))
                        }
                    }
                    put(
                        key,
                        CompletedRoad(
                            road = RenderedRoadSegment(
                                key = key,
                                points = points,
                                grade = grade,
                                kind = kind,
                            ),
                            count = count,
                        ),
                    )
                }
            }.also { require(input.available() == 0) }
        }
    }.getOrNull()

private fun DataOutputStream.writeSizedString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= RoadTraversalMaximumStringBytes)
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readSizedString(): String {
    val size = readInt()
    require(size in 0..RoadTraversalMaximumStringBytes)
    val bytes = ByteArray(size)
    readFully(bytes)
    return bytes.toString(Charsets.UTF_8)
}

internal const val RoadTraversalAlgorithmVersion = 2
private fun roadTraversalCacheZoom(cacheKey: String): Int? {
    val parts = cacheKey.split(':', limit = 3)
    return parts.takeIf {
        it.size == 3 && it[0].toIntOrNull() == RoadTraversalAlgorithmVersion
    }?.get(1)?.toIntOrNull()
}

private const val RoadTraversalDatabaseName = "road-traversal-cache.db"
private const val RoadTraversalDatabaseVersion = 3
private const val RoadProgressStateId = 1
private const val RoadTraversalEncodingVersion = 1
private const val RoadTraversalMaximumRoads = 100_000
private const val RoadTraversalMaximumCount = 1_000_000
private const val RoadTraversalMaximumPointsPerRoad = 100_000
private const val RoadTraversalMaximumStringBytes = 1_000_000
private const val LegacyRoadCoverageDatabaseName = "road-coverage-cache.db"
