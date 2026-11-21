package com.pockit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.data.EntryEntity
import com.pockit.app.data.PersonEntity
import com.pockit.app.data.ShareEntity
import com.pockit.app.ui.theme.SurfaceColor
import com.pockit.app.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val DATE_FMT_SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
fun EntryCard(
    entry: EntryEntity,
    shares: List<ShareEntity>,
    people: Map<Long, PersonEntity>,
    ownerName: String = "Me",
    viewingPersonId: Long? = null, // when non-null, show that person's perspective first
    modifier: Modifier = Modifier,
) {
    val expDate = LocalDate.ofEpochDay(entry.expenseDate)
    val payerName = displayName(entry.payerId, people, ownerName)
    val labelText = "${entry.label} - ${entry.labelNumber}"

    // Format amount with rupee symbol and comma separator
    val amountText = "₹${"%,d".format(entry.total)}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceColor)
            .padding(16.dp),
    ) {
        // Header row: label + amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = labelText,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                entry.purposeText?.let { purpose ->
                    if (purpose != entry.label) {
                        Text(
                            text = purpose,
                            color = White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            Text(
                text = amountText,
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Viewing person perspective (in thread)
        if (viewingPersonId != null && viewingPersonId != entry.payerId) {
            val myShare = shares.firstOrNull { it.personId == viewingPersonId }?.amount
            if (myShare != null) {
                Spacer(Modifier.height(4.dp))
                val owes = viewingPersonId != entry.payerId
                Text(
                    text = if (owes) "owes ₹${"%,d".format(myShare)}"
                    else "is owed ₹${"%,d".format(myShare)}",
                    color = White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Paid by $payerName",
            color = White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        val dateStr = if (entry.timeText != null) {
            "${expDate.format(DATE_FMT)} · ${entry.timeText}"
        } else expDate.format(DATE_FMT)
        Text(text = dateStr, color = White.copy(alpha = 0.6f), fontSize = 13.sp)

        entry.forDate?.let { forEpoch ->
            val forDate = LocalDate.ofEpochDay(forEpoch)
            Text(
                text = "For: ${forDate.format(DATE_FMT_SHORT)}",
                color = White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }

        // Shares
        if (shares.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            for (share in shares) {
                val name = displayName(share.personId, people, ownerName)
                Text(
                    text = "$name ₹${"%,d".format(share.amount)}",
                    color = White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }

        // Extra info and original text
        entry.extraInfo?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = it, color = White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.originalText,
            color = White.copy(alpha = 0.35f),
            fontSize = 12.sp,
        )
    }
}

fun displayName(personId: Long, people: Map<Long, PersonEntity>, ownerName: String): String {
    val p = people[personId] ?: return "Unknown"
    return if (p.isOwner) ownerName else p.mainName
}
