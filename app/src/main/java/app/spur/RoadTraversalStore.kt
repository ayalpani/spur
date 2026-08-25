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

internal data class RoadTraversalCellChange(
    val unchangedRoads: Int = 0,
    val increasedRoads: Int = 0,
    val decreasedRoads: Int = 0,
    val addedRoads: Int = 0,
    val removedRoads: Int = 0,
    val oldTraversalTotal: Long = 0,
    val newTraversalTotal: Long = 0,
) {
    val changedRoads: Int
        get() = increasedRoads + decreasedRoads + addedRoads + removedRoads
}

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
        createRoadRebuildStats(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion in 2..3 && newVersion >= 4) {
            if (oldVersion == 2) createRoadProgressState(db)
            createRoadRebuildStats(db)
            return
        }
        db.execSQL("DROP TABLE IF EXISTS road_traversal_cells")
        db.execSQL("DROP TABLE IF EXISTS road_progress_state")
        db.execSQL("DROP TABLE IF EXISTS road_rebuild_stats")
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

    private fun createRoadRebuildStats(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS road_rebuild_stats (
                id INTEGER PRIMARY KEY,
                compared_cells INTEGER NOT NULL,
                unchanged_roads INTEGER NOT NULL,
                increased_roads INTEGER NOT NULL,
                decreased_roads INTEGER NOT NULL,
                added_roads INTEGER NOT NULL,
                removed_roads INTEGER NOT NULL,
                old_traversal_total INTEGER NOT NULL,
                new_traversal_total INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun addRebuildStats(db: SQLiteDatabase, change: RoadTraversalCellChange) {
        db.insertWithOnConflict(
            "road_rebuild_stats",
            null,
            ContentValues().apply {
                put("id", RoadRebuildStatsId)
                put("compared_cells", 0)
                put("unchanged_roads", 0)
                put("increased_roads", 0)
                put("decreased_roads", 0)
                put("added_roads", 0)
                put("removed_roads", 0)
                put("old_traversal_total", 0)
                put("new_traversal_total", 0)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        db.execSQL(
            """
            UPDATE road_rebuild_stats
            SET compared_cells = compared_cells + 1,
                unchanged_roads = unchanged_roads + ?,
                increased_roads = increased_roads + ?,
                decreased_roads = decreased_roads + ?,
                added_roads = added_roads + ?,
                removed_roads = removed_roads + ?,
                old_traversal_total = old_traversal_total + ?,
                new_traversal_total = new_traversal_total + ?
            WHERE id = ?
            """.trimIndent(),
            arrayOf(
                change.unchangedRoads,
                change.increasedRoads,
                change.decreasedRoads,
                change.addedRoads,
                change.removedRoads,
                change.oldTraversalTotal,
                change.newTraversalTotal,
                RoadRebuildStatsId,
            ),
        )
    }

    @Synchronized
    fun traversals(cacheKey: String): CachedRoadTraversals? =
        readTraversals(readableDatabase, cacheKey)

    private fun readTraversals(
        db: SQLiteDatabase,
        cacheKey: String,
    ): CachedRoadTraversals? =
        db.query(
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            val legacyKey = previousRoadTraversalCacheKey(cacheKey)
            val legacy = legacyKey?.let { readTraversals(db, it) }
                ?.takeIf { it.fingerprint == traversals.fingerprint }
            db.insertWithOnConflict(
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
            if (legacy != null) {
                addRebuildStats(
                    db,
                    compareRoadTraversals(legacy.completedRoads, traversals.completedRoads),
                )
            }
            legacyKey?.let { db.delete("road_traversal_cells", "cache_key = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun historyBaseline(): RoadHistoryFingerprint? =
        readHistoryBaseline(readableDatabase)

    private fun readHistoryBaseline(db: SQLiteDatabase): RoadHistoryFingerprint? =
        db.query(
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (readHistoryBaseline(db)?.let { it != fingerprint } == true) {
                db.delete("road_rebuild_stats", null, null)
            }
            db.insertWithOnConflict(
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("road_traversal_cells", null, null)
            db.delete("road_rebuild_stats", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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

internal fun compareRoadTraversals(
    old: Map<String, CompletedRoad>,
    new: Map<String, CompletedRoad>,
): RoadTraversalCellChange {
    var unchanged = 0
    var increased = 0
    var decreased = 0
    var added = 0
    var removed = 0
    (old.keys + new.keys).forEach { key ->
        val oldCount = old[key]?.count
        val newCount = new[key]?.count
        when {
            oldCount == null -> added++
            newCount == null -> removed++
            oldCount == newCount -> unchanged++
            oldCount < newCount -> increased++
            else -> decreased++
        }
    }
    return RoadTraversalCellChange(
        unchangedRoads = unchanged,
        increasedRoads = increased,
        decreasedRoads = decreased,
        addedRoads = added,
        removedRoads = removed,
        oldTraversalTotal = old.values.sumOf { it.count.toLong() },
        newTraversalTotal = new.values.sumOf { it.count.toLong() },
    )
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

internal const val RoadTraversalAlgorithmVersion = 3
private const val PreviousRoadTraversalAlgorithmVersion = RoadTraversalAlgorithmVersion - 1

private fun previousRoadTraversalCacheKey(cacheKey: String): String? =
    cacheKey.substringAfter(':', missingDelimiterValue = "")
        .takeIf(String::isNotEmpty)
        ?.let { "$PreviousRoadTraversalAlgorithmVersion:$it" }

private fun roadTraversalCacheZoom(cacheKey: String): Int? {
    val parts = cacheKey.split(':', limit = 3)
    return parts.takeIf {
        it.size == 3 && it[0].toIntOrNull() == RoadTraversalAlgorithmVersion
    }?.get(1)?.toIntOrNull()
}

private const val RoadTraversalDatabaseName = "road-traversal-cache.db"
private const val RoadTraversalDatabaseVersion = 4
private const val RoadProgressStateId = 1
private const val RoadRebuildStatsId = 1
private const val RoadTraversalEncodingVersion = 1
private const val RoadTraversalMaximumRoads = 100_000
private const val RoadTraversalMaximumCount = 1_000_000
private const val RoadTraversalMaximumPointsPerRoad = 100_000
private const val RoadTraversalMaximumStringBytes = 1_000_000
private const val LegacyRoadCoverageDatabaseName = "road-coverage-cache.db"
