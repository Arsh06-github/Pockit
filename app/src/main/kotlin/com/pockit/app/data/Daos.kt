package com.pockit.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person WHERE deletedAt IS NULL ORDER BY mainName")
    fun observeAlive(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person ORDER BY mainName")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)
}

@Dao
interface NicknameDao {
    @Query("SELECT * FROM nickname WHERE personId = :personId")
    suspend fun getForPerson(personId: Long): List<NicknameEntity>

    @Query("SELECT * FROM nickname")
    suspend fun getAll(): List<NicknameEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(nickname: NicknameEntity): Long

    @Query("DELETE FROM nickname WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}

@Dao
interface GroupSpaceDao {
    @Query("SELECT * FROM group_space WHERE deletedAt IS NULL ORDER BY id DESC")
    fun observeAlive(): Flow<List<GroupSpaceEntity>>

    @Query("SELECT * FROM group_space WHERE id = :id")
    suspend fun getById(id: Long): GroupSpaceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: GroupSpaceEntity): Long

    @Update
    suspend fun update(group: GroupSpaceEntity)
}

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_member WHERE groupId = :groupId AND active = 1")
    suspend fun getActiveMembers(groupId: Long): List<GroupMemberEntity>

    @Query("SELECT * FROM group_member WHERE groupId = :groupId")
    suspend fun getAll(groupId: Long): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: GroupMemberEntity)

    @Query("UPDATE group_member SET active = :active WHERE groupId = :groupId AND personId = :personId")
    suspend fun setActive(groupId: Long, personId: Long, active: Boolean)
}

@Dao
interface EntryDao {
    // All entries in the general space (no group), latest at bottom.
    @Transaction
    @Query("SELECT * FROM entry WHERE groupId IS NULL ORDER BY expenseDate ASC, enteredAt ASC")
    fun observeGeneral(): Flow<List<EntryWithShares>>

    // Entries where :personId has a share — uses subquery on share, never a JOIN with @Relation.
    @Transaction
    @Query("""
        SELECT * FROM entry
        WHERE groupId IS NULL
        AND id IN (SELECT entryId FROM share WHERE personId = :personId)
        ORDER BY expenseDate ASC, enteredAt ASC
    """)
    fun observeGeneralForPerson(personId: Long): Flow<List<EntryWithShares>>

    // All entries in a group.
    @Transaction
    @Query("SELECT * FROM entry WHERE groupId = :groupId ORDER BY expenseDate ASC, enteredAt ASC")
    fun observeGroup(groupId: Long): Flow<List<EntryWithShares>>

    // Owner's expenses: entries in the general space where owner has a share.
    @Transaction
    @Query("""
        SELECT * FROM entry
        WHERE groupId IS NULL
        AND id IN (SELECT entryId FROM share WHERE personId = :ownerId)
        ORDER BY expenseDate ASC, enteredAt ASC
    """)
    fun observeMyExpenses(ownerId: Long): Flow<List<EntryWithShares>>

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Query("""
        SELECT COUNT(*) FROM entry
        WHERE label = :label AND expenseDate = :epochDay
        AND ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId)
        AND id != :excludeId
    """)
    suspend fun countSameLabelSameDaySpace(
        label: String,
        epochDay: Long,
        groupId: Long?,
        excludeId: Long,
    ): Int

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Entries involving :personId as payer or share-holder in a date range.
    @Transaction
    @Query("""
        SELECT * FROM entry
        WHERE expenseDate BETWEEN :fromDay AND :toDay
        AND ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId)
        AND (payerId = :personId OR id IN (SELECT entryId FROM share WHERE personId = :personId))
    """)
    suspend fun getEntriesForPersonInRange(
        personId: Long,
        fromDay: Long,
        toDay: Long,
        groupId: Long?,
    ): List<EntryWithShares>

    // Owner's entries in a date range.
    @Transaction
    @Query("""
        SELECT * FROM entry
        WHERE expenseDate BETWEEN :fromDay AND :toDay
        AND ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId)
        AND id IN (SELECT entryId FROM share WHERE personId = :ownerId)
    """)
    suspend fun getMyExpensesInRange(
        ownerId: Long,
        fromDay: Long,
        toDay: Long,
        groupId: Long?,
    ): List<EntryWithShares>
}

@Dao
interface ShareDao {
    @Query("SELECT * FROM share WHERE entryId = :entryId")
    suspend fun getForEntry(entryId: Long): List<ShareEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shares: List<ShareEntity>)

    @Query("DELETE FROM share WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)
}
