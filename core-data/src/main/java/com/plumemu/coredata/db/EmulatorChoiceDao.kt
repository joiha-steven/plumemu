package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Emulator per system and per game. System rows have `game_id` NULL; SQLite's unique index does
 * not stop two NULL rows, so every system write goes through one transaction that checks first.
 */
@Dao
abstract class EmulatorChoiceDao {
    @Query("SELECT * FROM emulator_choice WHERE system_id = :systemId AND game_id IS NULL")
    abstract suspend fun systemChoice(systemId: String): EmulatorChoiceEntity?

    @Query("SELECT * FROM emulator_choice WHERE game_id = :gameId")
    abstract suspend fun gameChoice(gameId: Long): EmulatorChoiceEntity?

    /**
     * Stores [emulatorId] as the automatic default of [systemId], or clears the automatic default
     * when it is null. A choice the person made is left as it is. Returns the choice in force.
     */
    @Transaction
    open suspend fun setAutomaticSystemDefault(systemId: String, emulatorId: String?): EmulatorChoiceEntity? {
        insertSystemIfMissing(systemId)
        val current = systemChoice(systemId)
        return when {
            current?.isPersonChoice == true -> current
            emulatorId == null -> {
                if (current != null) delete(current.id)
                null
            }
            current == null -> insertNew(systemId, emulatorId, isPersonChoice = false)
            current.emulatorId == emulatorId -> current
            else -> current.copy(emulatorId = emulatorId).also { update(it) }
        }
    }

    /** The person picked [emulatorId] for [systemId]; it outranks any automatic default. */
    @Transaction
    open suspend fun setPersonSystemChoice(systemId: String, emulatorId: String): EmulatorChoiceEntity {
        insertSystemIfMissing(systemId)
        val current = systemChoice(systemId)
            ?: return insertNew(systemId, emulatorId, isPersonChoice = true)
        return current.copy(emulatorId = emulatorId, isPersonChoice = true).also { update(it) }
    }

    /** The person picked [emulatorId] for one game ("Play with"); it outranks the system's emulator. */
    @Transaction
    open suspend fun setPersonGameChoice(gameId: Long, systemId: String, emulatorId: String): EmulatorChoiceEntity {
        insertSystemIfMissing(systemId)
        val current = gameChoice(gameId)
            ?: return insertNew(systemId, emulatorId, isPersonChoice = true, gameId = gameId)
        return current.copy(systemId = systemId, emulatorId = emulatorId, isPersonChoice = true).also { update(it) }
    }

    /**
     * The person's choice for [systemId] is forgotten (Settings › Emulators › Automatic): the
     * system's automatic default comes back at the next refresh or launch. An automatic row stays.
     */
    @Query("DELETE FROM emulator_choice WHERE system_id = :systemId AND game_id IS NULL AND is_person_choice = 1")
    abstract suspend fun clearPersonSystemChoice(systemId: String)

    /** The game goes back to its system's emulator. */
    @Query("DELETE FROM emulator_choice WHERE game_id = :gameId")
    abstract suspend fun clearGameChoice(gameId: Long)

    private suspend fun insertNew(
        systemId: String,
        emulatorId: String,
        isPersonChoice: Boolean,
        gameId: Long? = null,
    ): EmulatorChoiceEntity {
        val row = EmulatorChoiceEntity(
            systemId = systemId,
            gameId = gameId,
            emulatorId = emulatorId,
            isPersonChoice = isPersonChoice,
        )
        return row.copy(id = insert(row))
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(choice: EmulatorChoiceEntity): Long

    @Update
    protected abstract suspend fun update(choice: EmulatorChoiceEntity)

    @Query("DELETE FROM emulator_choice WHERE id = :id")
    protected abstract suspend fun delete(id: Long)

    // The system's display name comes from the platform data later; its id stands in until then.
    @Query("INSERT OR IGNORE INTO system (id, name) VALUES (:systemId, :systemId)")
    protected abstract suspend fun insertSystemIfMissing(systemId: String)
}
