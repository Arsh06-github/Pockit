package com.pockit.app.data

import android.content.Context
import com.pockit.app.ledger.Due
import com.pockit.app.ledger.EntryData
import com.pockit.app.ledger.Payment
import com.pockit.app.ledger.ShareData
import com.pockit.app.ledger.applyEditChange
import com.pockit.app.ledger.settleUp
import com.pockit.app.parser.EditChange
import com.pockit.app.parser.GroupRecord
import com.pockit.app.parser.ParserContext
import com.pockit.app.parser.PersonRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

class Repository(context: Context) {

    private val db = PockitDatabase.get(context)
    private val personDao = db.personDao()
    private val nicknameDao = db.nicknameDao()
    private val groupSpaceDao = db.groupSpaceDao()
    private val groupMemberDao = db.groupMemberDao()
    private val entryDao = db.entryDao()
    private val shareDao = db.shareDao()

    // ── Seed ──────────────────────────────────────────────────────────────────

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val existing = personDao.getAll()
        if (existing.isNotEmpty()) return@withContext
        for (seed in SEED_PEOPLE) {
            val id = personDao.insert(PersonEntity(
                mainName = seed.mainName,
                isOwner = seed.mainName == "Me",
                isVendor = seed.isVendor,
            ))
            for (nick in seed.nicknames) {
                nicknameDao.insert(NicknameEntity(personId = id, text = nick.lowercase()))
            }
        }
    }

    // ── Parser context ────────────────────────────────────────────────────────

    suspend fun buildParserContext(): ParserContext = withContext(Dispatchers.IO) {
        val people = personDao.getAll()
        val nicknames = nicknameDao.getAll()
        val nickMap = nicknames.groupBy { it.personId }
        val personRecords = people.map { p ->
            PersonRecord(
                id = p.id,
                mainName = p.mainName,
                isOwner = p.isOwner,
                isVendor = p.isVendor,
                deletedAt = p.deletedAt,
                nicknames = nickMap[p.id]?.map { it.text } ?: emptyList(),
            )
        }
        val groups = groupSpaceDao.observeAlive().first()
        val groupRecords = groups.map { g ->
            val members = groupMemberDao.getActiveMembers(g.id).map { it.personId }.toSet()
            GroupRecord(id = g.id, name = g.name, deletedAt = g.deletedAt, memberIds = members)
        }
        ParserContext(
            people = personRecords,
            groups = groupRecords,
            purposeMap = buildPurposeMap(),
            cityMap = CITY_MAP,
            today = LocalDate.now(),
        )
    }

    // ── People ────────────────────────────────────────────────────────────────

    fun observeAlivePeople(): Flow<List<PersonEntity>> = personDao.observeAlive()

    suspend fun addPerson(mainName: String, nicknames: List<String>): Long =
        withContext(Dispatchers.IO) {
            val id = personDao.insert(PersonEntity(mainName = mainName, isOwner = false, isVendor = false))
            for (nick in nicknames) nicknameDao.insert(NicknameEntity(personId = id, text = nick.lowercase()))
            id
        }

    suspend fun updatePerson(id: Long, mainName: String, nicknames: List<String>) =
        withContext(Dispatchers.IO) {
            val existing = personDao.getById(id) ?: return@withContext
            personDao.update(existing.copy(mainName = mainName))
            nicknameDao.deleteForPerson(id)
            for (nick in nicknames) nicknameDao.insert(NicknameEntity(personId = id, text = nick.lowercase()))
        }

    suspend fun deletePerson(id: Long) = withContext(Dispatchers.IO) {
        val existing = personDao.getById(id) ?: return@withContext
        // Nicknames deleted via CASCADE on FK
        nicknameDao.deleteForPerson(id)
        personDao.update(existing.copy(deletedAt = System.currentTimeMillis()))
    }

    suspend fun getNicknames(personId: Long): List<String> = withContext(Dispatchers.IO) {
        nicknameDao.getForPerson(personId).map { it.text }
    }

    suspend fun getPersonById(id: Long): PersonEntity? = withContext(Dispatchers.IO) {
        personDao.getById(id)
    }

    suspend fun getAllNicknames(): List<NicknameEntity> = withContext(Dispatchers.IO) {
        nicknameDao.getAll()
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    fun observeAliveGroups(): Flow<List<GroupSpaceEntity>> = groupSpaceDao.observeAlive()

    suspend fun createGroup(name: String, memberIds: List<Long>): Long =
        withContext(Dispatchers.IO) {
            val id = groupSpaceDao.insert(GroupSpaceEntity(name = name))
            for (memberId in memberIds) {
                groupMemberDao.upsert(GroupMemberEntity(groupId = id, personId = memberId, active = true))
            }
            id
        }

    suspend fun updateGroup(id: Long, name: String, memberIds: List<Long>) =
        withContext(Dispatchers.IO) {
            val existing = groupSpaceDao.getById(id) ?: return@withContext
            groupSpaceDao.update(existing.copy(name = name))
            val current = groupMemberDao.getAll(id)
            val currentIds = current.map { it.personId }.toSet()
            val newIds = memberIds.toSet()
            for (memberId in newIds) {
                groupMemberDao.upsert(GroupMemberEntity(groupId = id, personId = memberId, active = true))
            }
            for (memberId in currentIds - newIds) {
                groupMemberDao.setActive(id, memberId, false)
            }
        }

    suspend fun deleteGroup(id: Long) = withContext(Dispatchers.IO) {
        val existing = groupSpaceDao.getById(id) ?: return@withContext
        groupSpaceDao.update(existing.copy(deletedAt = System.currentTimeMillis()))
    }

    suspend fun getActiveGroupMembers(groupId: Long): List<Long> = withContext(Dispatchers.IO) {
        groupMemberDao.getActiveMembers(groupId).map { it.personId }
    }

    // ── Entries ───────────────────────────────────────────────────────────────

    fun observeGeneralThread(personId: Long): Flow<List<EntryWithShares>> =
        entryDao.observeGeneralForPerson(personId)

    fun observeGroupThread(groupId: Long): Flow<List<EntryWithShares>> =
        entryDao.observeGroup(groupId)

    fun observeMyExpenses(ownerId: Long): Flow<List<EntryWithShares>> =
        entryDao.observeMyExpenses(ownerId)

    suspend fun saveEntry(
        parsed: com.pockit.app.parser.ParsedEntry,
        groupId: Long?,
        now: Long,
    ): Long = withContext(Dispatchers.IO) {
        val epochDay = parsed.expenseDate.toEpochDay()
        val labelCount = entryDao.countSameLabelSameDaySpace(
            parsed.label, epochDay, groupId, excludeId = 0L
        ) + 1

        val entryId = entryDao.insert(EntryEntity(
            kind = parsed.kind.name,
            groupId = groupId,
            payerId = parsed.payerId,
            receiverId = parsed.receiverId,
            vendorId = parsed.vendorId,
            total = parsed.total,
            label = parsed.label,
            labelNumber = labelCount,
            purposeText = parsed.purposeText,
            extraInfo = parsed.extraInfo,
            expenseDate = epochDay,
            forDate = parsed.forDate?.toEpochDay(),
            timeText = parsed.timeText,
            originalText = parsed.originalText,
            enteredAt = now,
        ))

        shareDao.insertAll(parsed.participants.map { ShareEntity(entryId, it.personId, it.amount) })

        // If this is a group entry, ensure all named people are active members
        if (groupId != null) {
            for (share in parsed.participants) {
                val current = groupMemberDao.getAll(groupId)
                val existing = current.firstOrNull { it.personId == share.personId }
                if (existing == null || !existing.active) {
                    groupMemberDao.upsert(GroupMemberEntity(groupId, share.personId, active = true))
                }
            }
            parsed.receiverId?.let { receiverId ->
                val current = groupMemberDao.getAll(groupId)
                if (current.none { it.personId == receiverId && it.active }) {
                    groupMemberDao.upsert(GroupMemberEntity(groupId, receiverId, active = true))
                }
            }
        }

        entryId
    }

    suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
        shareDao.deleteForEntry(entryId)
        entryDao.deleteById(entryId)
    }

    suspend fun updateEntry(entryId: Long, entry: EntryEntity, shares: List<ShareEntity>) =
        withContext(Dispatchers.IO) {
            entryDao.update(entry)
            shareDao.deleteForEntry(entryId)
            shareDao.insertAll(shares)
        }

    suspend fun getEntryById(id: Long): EntryEntity? = withContext(Dispatchers.IO) {
        entryDao.getById(id)
    }

    suspend fun getSharesForEntry(entryId: Long): List<ShareEntity> = withContext(Dispatchers.IO) {
        shareDao.getForEntry(entryId)
    }

    // ── Edit at repository boundary ───────────────────────────────────────────
    // Maps Room entities → plain models, delegates to ledger, maps back and saves.

    suspend fun applyAndSaveEdit(ews: EntryWithShares, change: EditChange): Boolean =
        withContext(Dispatchers.IO) {
            val entryData = ews.entry.toData()
            val sharesData = ews.shares.map { it.toData() }
            val (newEntry, newShares) = applyEditChange(change, entryData, sharesData) ?: return@withContext false
            entryDao.update(newEntry.toEntity())
            shareDao.deleteForEntry(ews.entry.id)
            shareDao.insertAll(newShares.map { it.toEntity() })
            true
        }

    private fun EntryEntity.toData() = EntryData(
        id, kind, groupId, payerId, receiverId, vendorId, total, label, labelNumber,
        purposeText, extraInfo, expenseDate, forDate, timeText, originalText, enteredAt,
    )

    private fun EntryData.toEntity() = EntryEntity(
        id, kind, groupId, payerId, receiverId, vendorId, total, label, labelNumber,
        purposeText, extraInfo, expenseDate, forDate, timeText, originalText, enteredAt,
    )

    private fun ShareEntity.toData() = ShareData(entryId, personId, amount)
    private fun ShareData.toEntity() = ShareEntity(entryId, personId, amount)

    // ── Entry queries for commands ────────────────────────────────────────────
    suspend fun getGeneralEntries(): List<EntryWithShares> = withContext(Dispatchers.IO) {
        entryDao.observeGeneral().first()
    }

    suspend fun getGroupEntries(groupId: Long): List<EntryWithShares> = withContext(Dispatchers.IO) {
        entryDao.observeGroup(groupId).first()
    }

    // Collect all dues for a space: personA owes personB amount.
    suspend fun getDues(groupId: Long?): List<Due> = withContext(Dispatchers.IO) {
        val entries = if (groupId == null) entryDao.observeGeneral().first()
        else entryDao.observeGroup(groupId).first()

        val dues = mutableListOf<Due>()
        for (ews in entries) {
            val entry = ews.entry
            if (entry.kind == "TRANSFER") {
                // Transfer: receiver owes payer
                entry.receiverId?.let { dues.add(Due(it, entry.payerId, entry.total)) }
            } else {
                // Expense: each participant (not the payer) owes payer their share
                for (share in ews.shares) {
                    if (share.personId != entry.payerId) {
                        dues.add(Due(share.personId, entry.payerId, share.amount))
                    }
                }
            }
        }
        dues
    }

    suspend fun getSettleUp(groupId: Long?): List<Payment> =
        settleUp(getDues(groupId))

    // ── Clear ─────────────────────────────────────────────────────────────────

    suspend fun clearPersonRange(
        personId: Long,
        from: LocalDate,
        to: LocalDate,
        groupId: Long?,
        carryForward: Boolean,
        now: Long,
    ) = withContext(Dispatchers.IO) {
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        val entries = entryDao.getEntriesForPersonInRange(personId, fromDay, toDay, groupId)

        if (carryForward) {
            val dues = mutableListOf<Due>()
            for (ews in entries) {
                val entry = ews.entry
                if (entry.kind == "TRANSFER") {
                    entry.receiverId?.let { dues.add(Due(it, entry.payerId, entry.total)) }
                } else {
                    for (share in ews.shares) {
                        if (share.personId != entry.payerId) {
                            dues.add(Due(share.personId, entry.payerId, share.amount))
                        }
                    }
                }
            }
            val payments = settleUp(dues)
            for (payment in payments) {
                entryDao.insert(EntryEntity(
                    kind = "TRANSFER",
                    groupId = groupId,
                    payerId = payment.fromId,
                    receiverId = payment.toId,
                    vendorId = null,
                    total = payment.amount,
                    label = "Carry forward",
                    labelNumber = 1,
                    purposeText = null,
                    extraInfo = null,
                    expenseDate = toDay,
                    forDate = null,
                    timeText = null,
                    originalText = "Carry forward",
                    enteredAt = now,
                ))
            }
        }

        for (ews in entries) {
            shareDao.deleteForEntry(ews.entry.id)
            entryDao.deleteById(ews.entry.id)
        }
    }

    suspend fun clearMyExpensesRange(
        ownerId: Long,
        from: LocalDate,
        to: LocalDate,
        groupId: Long?,
        carryForward: Boolean,
        now: Long,
    ) = withContext(Dispatchers.IO) {
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        val entries = entryDao.getMyExpensesInRange(ownerId, fromDay, toDay, groupId)

        if (carryForward) {
            val dues = mutableListOf<Due>()
            for (ews in entries) {
                val entry = ews.entry
                for (share in ews.shares) {
                    if (share.personId != entry.payerId) {
                        dues.add(Due(share.personId, entry.payerId, share.amount))
                    }
                }
            }
            val payments = settleUp(dues)
            for (payment in payments) {
                entryDao.insert(EntryEntity(
                    kind = "TRANSFER",
                    groupId = groupId,
                    payerId = payment.fromId,
                    receiverId = payment.toId,
                    vendorId = null,
                    total = payment.amount,
                    label = "Carry forward",
                    labelNumber = 1,
                    purposeText = null,
                    extraInfo = null,
                    expenseDate = toDay,
                    forDate = null,
                    timeText = null,
                    originalText = "Carry forward",
                    enteredAt = now,
                ))
            }
        }

        for (ews in entries) {
            shareDao.deleteForEntry(ews.entry.id)
            entryDao.deleteById(ews.entry.id)
        }
    }
}
