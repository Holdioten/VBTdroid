package com.vbtdroid.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `duplicate set insert aborts without deleting its reps`() = runBlocking {
        val session = SessionEntity(sessionId = "session-1")
        dao.insertSession(session)
        val set = SetEntity(
            setId = "set-1", sessionId = session.sessionId, lift = "SQUAT",
            metricsJson = "{}", qualityJson = "{}",
        )
        dao.insertSet(set)
        dao.insertReps(
            listOf(RepEntity(setId = set.setId, repIndex = 1, metricsJson = "{}", qualityJson = "{}")),
        )

        try {
            dao.insertSet(set.copy(metricsJson = "{\"edited\":true}"))
            throw AssertionError("Duplicate SetEntity insert should abort")
        } catch (_: SQLiteConstraintException) {
            // Expected: existing set and reps remain untouched.
        }

        assertEquals(1, dao.repsForSet(set.setId).size)
        assertEquals("{}", dao.setsForLift("SQUAT").single().metricsJson)
    }

    @Test
    fun `explicit set update preserves child reps`() = runBlocking {
        val session = SessionEntity(sessionId = "session-2")
        dao.insertSession(session)
        val set = SetEntity(
            setId = "set-2", sessionId = session.sessionId, lift = "BENCH_PRESS",
            metricsJson = "{}", qualityJson = "{}",
        )
        dao.insertSet(set)
        dao.insertReps(
            listOf(RepEntity(setId = set.setId, repIndex = 1, metricsJson = "{}", qualityJson = "{}")),
        )

        assertEquals(1, dao.updateSet(set.copy(metricsJson = "{\"edited\":true}")))
        assertEquals(1, dao.repsForSet(set.setId).size)
        assertEquals("{\"edited\":true}", dao.setsForLift("BENCH_PRESS").single().metricsJson)
    }
}
