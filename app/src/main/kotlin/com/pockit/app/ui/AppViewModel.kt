package com.pockit.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pockit.app.data.EntryEntity
import com.pockit.app.data.EntryWithShares
import com.pockit.app.data.GroupSpaceEntity
import com.pockit.app.data.HOME_MESSAGES
import com.pockit.app.data.PersonEntity
import com.pockit.app.data.Repository
import com.pockit.app.ledger.Payment
import com.pockit.app.parser.BalanceOneCommand
import com.pockit.app.parser.BalanceTwoCommand
import com.pockit.app.parser.ClearCommand
import com.pockit.app.parser.ClearScope
import com.pockit.app.parser.Command
import com.pockit.app.parser.DateRangeCommand
import com.pockit.app.parser.DeleteCommand
import com.pockit.app.parser.EditCommand
import com.pockit.app.parser.LastNCommand
import com.pockit.app.parser.MessageParseResult
import com.pockit.app.parser.NeedsAnswer
import com.pockit.app.parser.ParserContext
import com.pockit.app.parser.SearchCommand
import com.pockit.app.parser.SettleUpCommand
import com.pockit.app.parser.EditChange
import com.pockit.app.parser.parseEditChange
import com.pockit.app.parser.parseMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SendResult {
    object Sent : SendResult()
    data class TypoErrors(val lines: List<String>, val goodSaved: Boolean) : SendResult()
    data class NeedsDialog(val item: NeedsAnswer) : SendResult()
}

sealed class SheetContent {
    data class SearchResults(val entries: List<EntryWithShares>, val peopleMap: Map<Long, PersonEntity>) : SheetContent()
    data class BalanceTwo(val personAName: String, val personBName: String, val net: Int, val entries: List<EntryWithShares>) : SheetContent()
    data class BalanceOne(val personName: String, val generalNet: Int, val groupNets: List<Pair<String, Int>>) : SheetContent()
    data class SettleUp(val payments: List<Payment>, val peopleMap: Map<Long, PersonEntity>) : SheetContent()
    data class EntryTable(val entries: List<EntryWithShares>, val peopleMap: Map<Long, PersonEntity>) : SheetContent()
    object NothingPending : SheetContent()
    object NothingFound : SheetContent()
}

data class PendingClear(
    val command: ClearCommand,
    val pairNets: List<Pair<Pair<Long, Long>, Int>>,
    val peopleMap: Map<Long, PersonEntity>,
)

data class PendingEditPickOne(
    val candidates: List<EntryWithShares>,
    val editChange: EditChange,
    val peopleMap: Map<Long, PersonEntity>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository(app)

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    private val _sendResult = MutableStateFlow<SendResult?>(null)
    val sendResult: StateFlow<SendResult?> = _sendResult.asStateFlow()

    private val _sheetContent = MutableStateFlow<SheetContent?>(null)
    val sheetContent: StateFlow<SheetContent?> = _sheetContent.asStateFlow()

    private val _pendingClear = MutableStateFlow<PendingClear?>(null)
    val pendingClear: StateFlow<PendingClear?> = _pendingClear.asStateFlow()

    private val _pendingDelete = MutableStateFlow<EntryEntity?>(null)
    val pendingDelete: StateFlow<EntryEntity?> = _pendingDelete.asStateFlow()

    private val _pendingEditPickOne = MutableStateFlow<PendingEditPickOne?>(null)
    val pendingEditPickOne: StateFlow<PendingEditPickOne?> = _pendingEditPickOne.asStateFlow()

    val people: StateFlow<List<PersonEntity>> = repo.observeAlivePeople()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groups: StateFlow<List<GroupSpaceEntity>> = repo.observeAliveGroups()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Flat map of lowercase nickname -> mainName of owner. Refreshed each time
    // parserCtx is rebuilt (after any person add/edit/delete).
    private val _nickOwners = MutableStateFlow<Map<String, String>>(emptyMap())
    val nickOwners: StateFlow<Map<String, String>> = _nickOwners.asStateFlow()

    val homeMessage: String = HOME_MESSAGES.random()

    private var parserCtx: ParserContext? = null

    init {
        viewModelScope.launch {
            repo.seedIfNeeded()
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
        }
    }

    private fun rebuildNickOwners(ctx: ParserContext) {
        _nickOwners.value = ctx.people
            .filter { it.deletedAt == null }
            .flatMap { p -> p.nicknames.map { nick -> nick.lowercase() to p.mainName } }
            .toMap()
    }

    fun navigate(screen: Screen) { _screen.value = screen }
    fun openPanel() { _panelOpen.value = true }
    fun closePanel() { _panelOpen.value = false }
    fun clearSendResult() { _sendResult.value = null }
    fun closeSheet() { _sheetContent.value = null }
    fun clearPendingClear() { _pendingClear.value = null }
    fun clearPendingDelete() { _pendingDelete.value = null }
    fun clearPendingEditPickOne() { _pendingEditPickOne.value = null }

    fun sendMessage(text: String, impliedGroupId: Long? = null) {
        viewModelScope.launch {
            val ctx = parserCtx ?: repo.buildParserContext().also { parserCtx = it }
            val result: MessageParseResult = parseMessage(text, ctx)
            val now = System.currentTimeMillis()

            for (entry in result.entries) {
                val resolvedGroupId = resolveGroupId(entry.groupTag) ?: impliedGroupId
                repo.saveEntry(entry, resolvedGroupId, now)
            }

            if (result.commands.isNotEmpty()) {
                handleCommand(result.commands.first(), impliedGroupId)
                return@launch
            }

            when {
                result.needsAnswers.isNotEmpty() ->
                    _sendResult.value = SendResult.NeedsDialog(result.needsAnswers.first())
                result.typos.isNotEmpty() ->
                    _sendResult.value = SendResult.TypoErrors(
                        lines = result.typos.map { it.line },
                        goodSaved = result.entries.isNotEmpty(),
                    )
                else -> {
                    _sendResult.value = SendResult.Sent
                    parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
                }
            }
        }
    }

    private suspend fun handleCommand(cmd: Command, impliedGroupId: Long?) {
        val allPeople = repo.observeAlivePeople().first()
        val peopleMap = allPeople.associateBy { it.id }

        when (cmd) {
            is SearchCommand -> {
                val groupId = resolveGroupId(cmd.groupTag) ?: impliedGroupId
                val scope = if (groupId == null) repo.getGeneralEntries()
                            else repo.getGroupEntries(groupId)
                val query = cmd.description.lowercase()
                val matched = scope.filter { ews ->
                    val e = ews.entry
                    e.label.lowercase().contains(query) ||
                    (e.purposeText?.lowercase()?.contains(query) == true) ||
                    e.originalText.lowercase().contains(query)
                }
                _sheetContent.value = if (matched.isEmpty()) SheetContent.NothingFound
                else SheetContent.SearchResults(matched, peopleMap)
            }

            is SettleUpCommand -> {
                val groupId = resolveGroupId(cmd.groupTag) ?: impliedGroupId
                val payments = repo.getSettleUp(groupId)
                _sheetContent.value = if (payments.isEmpty()) SheetContent.NothingPending
                else SheetContent.SettleUp(payments, peopleMap)
            }

            is BalanceTwoCommand -> {
                val groupId = impliedGroupId
                val dues = repo.getDues(groupId)
                val aId = cmd.personAId; val bId = cmd.personBId
                val aOwesB = dues.filter { it.debtorId == aId && it.creditorId == bId }.sumOf { it.amount }
                val bOwesA = dues.filter { it.debtorId == bId && it.creditorId == aId }.sumOf { it.amount }
                val net = aOwesB - bOwesA
                val nameA = peopleMap[aId]?.let { if (it.isOwner) "Me" else it.mainName } ?: "Unknown"
                val nameB = peopleMap[bId]?.let { if (it.isOwner) "Me" else it.mainName } ?: "Unknown"
                _sheetContent.value = if (net == 0) SheetContent.NothingPending
                else SheetContent.BalanceTwo(nameA, nameB, net, emptyList())
            }

            is BalanceOneCommand -> {
                val personId = cmd.personId
                val generalDues = repo.getDues(null)
                val generalNet = generalDues.filter {
                    (it.debtorId == personId) || (it.creditorId == personId)
                }.sumOf { if (it.debtorId == personId) it.amount else -it.amount }
                val name = peopleMap[personId]?.let { if (it.isOwner) "Me" else it.mainName } ?: "Unknown"
                _sheetContent.value = SheetContent.BalanceOne(name, generalNet, emptyList())
            }

            is DeleteCommand -> {
                val groupId = impliedGroupId
                val scope = if (groupId == null) repo.getGeneralEntries()
                            else repo.getGroupEntries(groupId)
                val query = cmd.description.lowercase()
                val matched = scope.filter { ews ->
                    val e = ews.entry
                    e.label.lowercase().contains(query) ||
                    (e.purposeText?.lowercase()?.contains(query) == true)
                }
                if (matched.size == 1) _pendingDelete.value = matched.first().entry
                else if (matched.isEmpty()) _sendResult.value = SendResult.TypoErrors(listOf(cmd.description), false)
            }

            is ClearCommand -> {
                val groupId = resolveGroupId(cmd.groupTag) ?: impliedGroupId
                val dues = repo.getDues(groupId)
                val pairNets = dues.groupBy { it.debtorId to it.creditorId }
                    .map { (pair, list) -> pair to list.sumOf { it.amount } }
                    .filter { it.second > 0 }
                _pendingClear.value = PendingClear(cmd, pairNets, peopleMap)
            }

            is EditCommand -> {
                val groupId = resolveGroupId(cmd.groupTag) ?: impliedGroupId
                val scope = if (groupId == null) repo.getGeneralEntries()
                            else repo.getGroupEntries(groupId)
                val ctx2 = parserCtx ?: repo.buildParserContext().also { parserCtx = it }
                val candidates = findEntriesForDescription(cmd.description, scope.map { it.entry }, ctx2)
                if (candidates.isEmpty()) {
                    _sendResult.value = SendResult.TypoErrors(listOf(cmd.description), false)
                    return
                }
                // Parse the change before showing pick dialog so we fail fast on bad syntax
                val firstEws = scope.first { it.entry.id == candidates[0].id }
                val change = parseEditChange(cmd.changes, ctx2)
                if (change == null) {
                    _sendResult.value = SendResult.TypoErrors(listOf(cmd.changes), false)
                    return
                }
                if (candidates.size > 1) {
                    // Multiple matches â€” ask the user to pick one
                    val candidateEws = candidates.map { e -> scope.first { it.entry.id == e.id } }
                    _pendingEditPickOne.value = PendingEditPickOne(candidateEws, change, peopleMap)
                    return
                }
                applyAndSaveEdit(firstEws, change)
            }

            is LastNCommand -> {
                val groupId = impliedGroupId
                val scope = if (groupId == null) repo.getGeneralEntries()
                            else repo.getGroupEntries(groupId)
                val taken = scope.takeLast(cmd.n)
                _sheetContent.value = SheetContent.EntryTable(taken, peopleMap)
            }

            is DateRangeCommand -> {
                val groupId = impliedGroupId
                val fromDay = cmd.from.toEpochDay(); val toDay = cmd.to.toEpochDay()
                val scope = if (groupId == null) repo.getGeneralEntries()
                            else repo.getGroupEntries(groupId)
                val filtered = scope.filter { it.entry.expenseDate in fromDay..toDay }
                _sheetContent.value = SheetContent.EntryTable(filtered, peopleMap)
            }
        }
    }

    fun confirmDelete(entryId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteEntry(entryId)
            _pendingDelete.value = null
            _sendResult.value = SendResult.Sent
            onDone()
        }
    }

    fun confirmClearSettled(onDone: () -> Unit) {
        viewModelScope.launch {
            val cmd = _pendingClear.value?.command ?: return@launch
            val groupId = resolveGroupId(cmd.groupTag)
            val now = System.currentTimeMillis()
            val owner = repo.observeAlivePeople().first().firstOrNull { it.isOwner } ?: return@launch
            when (val scope = cmd.scope) {
                is ClearScope.Person -> repo.clearPersonRange(scope.personId, cmd.from, cmd.to, groupId, false, now)
                is ClearScope.MyExpenses -> repo.clearMyExpensesRange(owner.id, cmd.from, cmd.to, groupId, false, now)
            }
            _pendingClear.value = null
            _sendResult.value = SendResult.Sent
            onDone()
        }
    }

    fun confirmClearCarryForward(onDone: () -> Unit) {
        viewModelScope.launch {
            val cmd = _pendingClear.value?.command ?: return@launch
            val groupId = resolveGroupId(cmd.groupTag)
            val now = System.currentTimeMillis()
            val owner = repo.observeAlivePeople().first().firstOrNull { it.isOwner } ?: return@launch
            when (val scope = cmd.scope) {
                is ClearScope.Person -> repo.clearPersonRange(scope.personId, cmd.from, cmd.to, groupId, true, now)
                is ClearScope.MyExpenses -> repo.clearMyExpensesRange(owner.id, cmd.from, cmd.to, groupId, true, now)
            }
            _pendingClear.value = null
            _sendResult.value = SendResult.Sent
            onDone()
        }
    }

    private suspend fun applyAndSaveEdit(ews: EntryWithShares, change: EditChange) {
        val ok = repo.applyAndSaveEdit(ews, change)
        if (!ok) {
            _sendResult.value = SendResult.TypoErrors(listOf("Invalid change"), false)
            return
        }
        parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
        _sendResult.value = SendResult.Sent
    }

    fun confirmEditPickOne(entryId: Long) {
        viewModelScope.launch {
            val pending = _pendingEditPickOne.value ?: return@launch
            val ews = pending.candidates.firstOrNull { it.entry.id == entryId } ?: return@launch
            _pendingEditPickOne.value = null
            applyAndSaveEdit(ews, pending.editChange)
        }
    }

    private fun resolveGroupId(groupTag: String?): Long? {
        if (groupTag == null) return null
        return groups.value.firstOrNull { it.name == groupTag }?.id
    }

    fun addPerson(mainName: String, nicknames: List<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.addPerson(mainName, nicknames)
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
            onDone()
        }
    }

    fun updatePerson(id: Long, mainName: String, nicknames: List<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updatePerson(id, mainName, nicknames)
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
            onDone()
        }
    }

    fun deletePerson(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deletePerson(id)
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
            onDone()
        }
    }

    fun createGroup(name: String, memberIds: List<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createGroup(name, memberIds)
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
            onDone()
        }
    }

    fun updateGroup(id: Long, name: String, memberIds: List<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updateGroup(id, name, memberIds)
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
            onDone()
        }
    }

    fun deleteGroup(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteGroup(id)
            parserCtx = repo.buildParserContext().also { rebuildNickOwners(it) }
            onDone()
        }
    }

    fun getEntriesForThread(personId: Long) = repo.observeGeneralThread(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getEntriesForGroup(groupId: Long) = repo.observeGroupThread(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private fun findEntriesForDescription(
    description: String,
    entries: List<EntryEntity>,
    ctx: ParserContext,
): List<EntryEntity> {
    val lower = description.lowercase()
    return entries.filter { e ->
        val labelMatch = e.label.lowercase().contains(lower) ||
            (e.purposeText?.lowercase()?.contains(lower) == true)
        val originalMatch = e.originalText.lowercase().contains(lower)
        labelMatch || originalMatch
    }
}

