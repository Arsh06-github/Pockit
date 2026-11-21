package com.pockit.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mainName: String,
    val isOwner: Boolean,
    val isVendor: Boolean,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "nickname",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("personId"), Index("text", unique = true)],
)
data class NicknameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val text: String, // stored lower case
)

@Entity(tableName = "group_space")
data class GroupSpaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "group_member",
    primaryKeys = ["groupId", "personId"],
    foreignKeys = [
        ForeignKey(entity = GroupSpaceEntity::class, parentColumns = ["id"], childColumns = ["groupId"]),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("groupId"), Index("personId")],
)
data class GroupMemberEntity(
    val groupId: Long,
    val personId: Long,
    val active: Boolean,
)

@Entity(
    tableName = "entry",
    indices = [Index("groupId", "expenseDate"), Index("enteredAt")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String, // "EXPENSE" or "TRANSFER"
    val groupId: Long?,
    val payerId: Long,
    val receiverId: Long?,
    val vendorId: Long?,
    val total: Int,
    val label: String,
    val labelNumber: Int,
    val purposeText: String?,
    val extraInfo: String?,
    val expenseDate: Long, // epoch day
    val forDate: Long?,    // epoch day
    val timeText: String?,
    val originalText: String,
    val enteredAt: Long,   // millis
)

@Entity(
    tableName = "share",
    primaryKeys = ["entryId", "personId"],
    foreignKeys = [
        ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("entryId"), Index("personId")],
)
data class ShareEntity(
    val entryId: Long,
    val personId: Long,
    val amount: Int,
)

data class EntryWithShares(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "entryId",
    )
    val shares: List<ShareEntity>,
)
