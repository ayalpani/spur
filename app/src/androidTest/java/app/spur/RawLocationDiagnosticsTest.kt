package app.spur

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RawLocationStorageTest {
    @Test
    fun rawMeasurementsSurviveReopenWithoutChangingTheRouteAndExpireOrDelete() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "raw-location-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        }
        var store = TourStore(context)
        try {
            // Simulate a v4 installation that predates this additive table.
            store.writableDatabase.execSQL("DROP TABLE raw_locations")
            store.close()
            store = TourStore(context)
            val now = System.currentTimeMillis()
            val id = store.startTour(now)
            fun fix(offset: Long, accuracy: Float, latitude: Double = 0.0) = Location("test").apply {
                this.latitude = latitude
                longitude = 0.0
                time = now + offset
                this.accuracy = accuracy
            }
            store.inLocationBatch {
                assertTrue(store.appendLocation(id, fix(0, 3f)))
                assertFalse(store.appendLocation(id, fix(2_000, 80f, 0.001)))
                assertTrue(store.appendLocation(id, fix(10_000, 3f, 0.001)))
            }
            assertEquals(2, store.points(id).size)
            store.close()
            store = TourStore(context)
            assertEquals(4, store.writableDatabase.version)
            val decisions = store.readableDatabase.rawQuery("SELECT decision FROM raw_locations ORDER BY id", null).use {
                buildList { while (it.moveToNext()) add(it.getString(0)) }
            }
            assertEquals(listOf("ACCEPTED", "POOR_ACCURACY", "ACCEPTED"), decisions)
            assertEquals(2, store.points(id).size)
            store.writableDatabase.execSQL("UPDATE raw_locations SET received_at = ? WHERE decision = 'POOR_ACCURACY'", arrayOf(now - RawLocationRetentionMillis - 1))
            store.writableDatabase.pruneRawLocations(now)
            fun count() = store.readableDatabase.rawQuery("SELECT COUNT(*) FROM raw_locations", null).use { it.moveToFirst(); it.getInt(0) }
            assertEquals(2, count())
            store.deleteTour(id)
            assertEquals(0, count())
            store.recordIgnoredLocation(id, fix(20_000, 3f), RawLocationDecision.STALE_SESSION)
            assertEquals(0, count())
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }
}
