package gov.cdc.prime.router.azure

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpResponseMessage
import gov.cdc.prime.router.ClientSource
import gov.cdc.prime.router.Organization
import gov.cdc.prime.router.QualityFilterResult
import gov.cdc.prime.router.Receiver
import gov.cdc.prime.router.Report
import gov.cdc.prime.router.ReportId
import gov.cdc.prime.router.Sender
import gov.cdc.prime.router.SettingsProvider
import gov.cdc.prime.router.azure.db.Tables
import gov.cdc.prime.router.azure.db.Tables.ACTION
import gov.cdc.prime.router.azure.db.Tables.ITEM_LINEAGE
import gov.cdc.prime.router.azure.db.Tables.REPORT_FILE
import gov.cdc.prime.router.azure.db.Tables.REPORT_LINEAGE
import gov.cdc.prime.router.azure.db.enums.TaskAction
import gov.cdc.prime.router.azure.db.tables.pojos.Action
import gov.cdc.prime.router.azure.db.tables.pojos.ItemLineage
import gov.cdc.prime.router.azure.db.tables.pojos.ReportFile
import gov.cdc.prime.router.azure.db.tables.pojos.ReportLineage
import gov.cdc.prime.router.azure.db.tables.pojos.Task
import gov.cdc.prime.router.azure.db.tables.records.ItemLineageRecord
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime

/**
 * This is a container class that holds information to be stored, about a single action,
 * as well as the reports that went into that Action, and were created by that Action.
 *
 * The idea is that, as an action progresses, call various track*(...) methods here to add additional information to
 * this container, in-memory only.
 *
 * Then when the action is done, call saveToDb(...) to plunk all the tracked information into the database.
 *
 */
class ActionHistory {
    // todo change to Logger
    private var context: ExecutionContext?

    /**
     * Throughout, using generated mutable jooq POJOs to store history information
     *
     */
    val action = Action()

    /*
     * Reports that are inputs to this action, from previous steps.
     * These reports are already in report_file.  For this action, we insert them as parents into
     * report_lineage.
     */
    val reportsIn = mutableMapOf<ReportId, ReportFile>()

    /*
     * Reports that are inputs to this action, from external source.
     * Note that this should be able to handle multiple submitted reports in one action.
     * For this action, we insert these into report_file, and as parents into report_lineage.
     */
    val reportsReceived = mutableMapOf<ReportId, ReportFile>()

    /*
     * New reports generated by this action.
     * For this action, we insert these into report_file, and as children into report_lineage.
     */
    val reportsOut = mutableMapOf<ReportId, ReportFile>()

    val filteredOutReports = mutableMapOf<ReportId, ReportFile>()

    val filteredReportRows = mutableMapOf<ReportId, List<QualityFilterResult>>()

    /**
     * Messages to be queued in an azure queue as part of the result of this action.
     */
    val messages = mutableListOf<Event>()

    /**
     *
     * Collection of all the parent-child report relationships created by this action.
     *
     * Note:  There is a strong OO argument that this list should be broken out into each individual child Report.kt.
     * (That is, every report should know its own parents!)
     * However, its here because there are Functions that do not create Report.kt objects.  For example, Send.
     * In addition, in-memory, reports get copied many times, with lots of parent-child relationships
     * that are error-prone to track.  Hiding the lineage data here helps ensure correctness and hide complexity.
     */
    private val reportLineages = mutableListOf<ReportLineage>()

    /**
     * Set of new parent->child Item mappings created by this Action.
     * Note this crucial assumption: the ordering of rows is fixed within any one report.
     */
    val itemLineages = mutableSetOf<ItemLineage>()

    constructor(taskAction: TaskAction, context: ExecutionContext? = null) {
        action.actionName = taskAction
        this.context = context
    }

    fun setActionType(taskAction: TaskAction) {
        action.actionName = taskAction
    }

    fun trackEvent(event: Event) {
        messages.add(event)
    }

    fun trackActionParams(request: HttpRequestMessage<String?>) {
        val factory = JsonFactory()
        val outStream = ByteArrayOutputStream()
        factory.createGenerator(outStream).use {
            it.useDefaultPrettyPrinter()
            it.writeStartObject()
            it.writeStringField("method", request.httpMethod.toString())
            it.writeObjectFieldStart("Headers")
            // remove secrets
            request.headers
                .filter { !it.key.contains("key") }
                .filter { !it.key.contains("cookie") }
                .forEach { (key, value) ->
                    it.writeStringField(key, value)
                }
            it.writeEndObject()
            it.writeObjectFieldStart("QueryParameters")
            // remove secrets
            request.queryParameters.filter { !it.key.contains("code") }.forEach { (key, value) ->
                it.writeStringField(key, value)
            }
            it.writeEndObject()
            it.writeEndObject()
        }
        action.contentLength = request.headers["content-length"]?.let {
            try { it.toInt() } catch (e: NumberFormatException) { null }
        }
        // capture the azure client IP but override with the first forwarded for if present
        action.senderIp = request.headers["x-azure-clientip"]?.take(ACTION.SENDER_IP.dataType.length())
        request.headers["x-forwarded-for"]?.let {
            action.senderIp = it.split(",").firstOrNull()?.trim()?.take(ACTION.SENDER_IP.dataType.length())
        }
        trackActionParams(outStream.toString())
    }

    /**
     * Always appends, to allow for actions that do a mix of work (eg, SEND)
     */
    fun trackActionParams(actionParams: String) {
        if (actionParams.isEmpty()) return
        val tmp = if (action.actionParams.isNullOrBlank()) actionParams else "${action.actionParams}, $actionParams"
        // kluge to get the max size of the varchar
        val max = ACTION.ACTION_PARAMS.dataType.length()
        // truncate if needed
        action.actionParams = tmp.chunked(size = max)[0]
    }

    /**
     * Always appends
     */
    fun trackActionResult(actionResult: String) {
        val tmp = if (action.actionResult.isNullOrBlank()) actionResult else "${action.actionResult}, $actionResult"
        val max = ACTION.ACTION_RESULT.dataType.length()
        action.actionResult = tmp.chunked(size = max)[0]
    }

    fun trackActionResult(httpResponseMessage: HttpResponseMessage) {
        trackActionResult(httpResponseMessage.status.toString() + ":\n" + httpResponseMessage.body.toString())
    }

    fun trackActionRequestResponse(request: HttpRequestMessage<String?>, response: HttpResponseMessage) {
        trackActionParams(request)
        trackActionResult(response)
    }

    /**
     * Parses the client parameter and sets the sending organization
     * and client in the action table.
     * @param clientParam the client header submitted with the report
     */
    fun trackActionSender(clientParam: String) {
        // only set the action properties if not null
        if (clientParam.isNotBlank()) {
            try {
                val (sendingOrg, sendingOrgClient) = Sender.parseFullName(clientParam)
                action.sendingOrg = sendingOrg.take(ACTION.SENDING_ORG.dataType.length())
                action.sendingOrgClient = sendingOrgClient.take(ACTION.SENDING_ORG_CLIENT.dataType.length())
            } catch (e: Exception) {
                this.context?.logger?.warning(
                    "Exception tracking sender: ${e.localizedMessage} ${e.stackTraceToString()}"
                )
            }
        }
    }

    /**
     * Set the http status and verbose JSON response in the action table.
     * @param response the response created while processing the submitted report
     * @param verboseResponse the generated verbose response with all details
     */
    fun trackActionResponse(response: HttpResponseMessage, verboseResponse: String) {
        action.httpStatus = response.status.value()
        if (!verboseResponse.isNullOrBlank())
            action.actionResponse = JSONB.valueOf(verboseResponse)
    }

    /**
     * Sanity check: No report can be tracked twice, either as an input or output.
     * Prevents at least tight loops, and other shenanigans.
     */
    private fun isReportAlreadyTracked(id: ReportId): Boolean {
        return reportsReceived.containsKey(id) ||
            reportsIn.containsKey(id) ||
            reportsOut.containsKey(id)
    }

    /**
     * track that this report is used in this Action.
     * Note: the report is already in the database.  Just need this for lineage purposes.
     */
    fun trackExistingInputReport(reportId: ReportId) {
        if (isReportAlreadyTracked(reportId)) {
            error("Bug:  attempt to track history of a report ($reportId) we've already associated with this action")
        }
        val reportFile = ReportFile()
        reportFile.reportId = reportId
        reportsIn[reportId] = reportFile
    }

    /**
     * Use this to record history info about a new externally submitted report.
     */
    fun trackExternalInputReport(report: Report, blobInfo: BlobAccess.BlobInfo) {
        if (isReportAlreadyTracked(report.id)) {
            error("Bug:  attempt to track history of a report ($report.id) we've already associated with this action")
        }

        val reportFile = ReportFile()
        reportFile.reportId = report.id
        reportFile.nextAction = TaskAction.none
        // todo Is there a better way to get the sendingOrg and sendingOrgClient?
        if (report.sources.size != 1) {
            error(
                "An external incoming report should have only one source.   " +
                    "Report ${report.id} had ${report.sources.size} sources"
            )
        }
        val source = (report.sources[0] as ClientSource)
        reportFile.sendingOrg = source.organization
        reportFile.sendingOrgClient = source.client
        reportFile.schemaName = report.schema.name
        reportFile.schemaTopic = report.schema.topic
        reportFile.bodyUrl = blobInfo.blobUrl
        reportFile.bodyFormat = blobInfo.format.toString()
        reportFile.blobDigest = blobInfo.digest
        reportFile.itemCount = report.itemCount
        reportsReceived[reportFile.reportId] = reportFile
        if (report.itemLineages != null)
            error("For report ${report.id}:  Externally submitted reports should never have item lineagee.")
    }

    fun trackFilteredReport(
        report: Report,
        receiver: Receiver,
    ) {
        val reportFile = ReportFile()
        reportFile.reportId = report.id
        reportFile.receivingOrg = receiver.organizationName
        reportFile.receivingOrgSvc = receiver.name
        reportFile.schemaName = report.schema.name
        reportFile.schemaTopic = report.schema.topic
        reportFile.itemCount = report.itemCount
        filteredOutReports[reportFile.reportId] = reportFile
        filteredReportRows[reportFile.reportId] = report.filteredItems
    }

    /**
     * Use this to record history info about an internally created report.
     * This also tracks the event to be queued later, as an azure message.
     */
    fun trackCreatedReport(
        event: Event,
        report: Report,
        receiver: Receiver,
        blobInfo: BlobAccess.BlobInfo,
    ) {
        if (isReportAlreadyTracked(report.id)) {
            error("Bug:  attempt to track history of a report ($report.id) we've already associated with this action")
        }

        val reportFile = ReportFile()
        reportFile.reportId = report.id
        reportFile.nextAction = event.eventAction.toTaskAction()
        reportFile.nextActionAt = event.at
        reportFile.receivingOrg = receiver.organizationName
        reportFile.receivingOrgSvc = receiver.name
        reportFile.schemaName = report.schema.name
        reportFile.schemaTopic = report.schema.topic
        reportFile.bodyUrl = blobInfo.blobUrl
        reportFile.bodyFormat = blobInfo.format.toString()
        reportFile.blobDigest = blobInfo.digest
        reportFile.itemCount = report.itemCount
        reportsOut[reportFile.reportId] = reportFile
        filteredReportRows[reportFile.reportId] = report.filteredItems
        trackItemLineages(report)
        trackEvent(event) // to be sent to queue later.
    }

    fun trackSentReport(
        receiver: Receiver,
        sentReportId: ReportId,
        fileName: String?,
        params: String,
        result: String,
        itemCount: Int
    ) {
        if (isReportAlreadyTracked(sentReportId)) {
            error(
                "Bug:  attempt to track history of a report ($sentReportId) " +
                    "we've already associated with this action"
            )
        }
        val reportFile = ReportFile()
        reportFile.reportId = sentReportId
        reportFile.receivingOrg = receiver.organizationName
        reportFile.receivingOrgSvc = receiver.name
        reportFile.schemaName = receiver.schemaName
        reportFile.schemaTopic = receiver.topic
        reportFile.externalName = fileName
        reportFile.transportParams = params
        reportFile.transportResult = result
        reportFile.bodyUrl = null
        reportFile.bodyFormat = receiver.format.toString()
        reportFile.blobDigest = null // no blob
        reportFile.itemCount = itemCount
        reportsOut[reportFile.reportId] = reportFile
    }

    /**
     * Note that confusingly the downloadedReportId is NOT the UUID of the blob that got downloaded.
     * Its a brand new UUID, that artificially represents the copy of the report that is now outside
     * of our custody.
     */
    fun trackDownloadedReport(
        header: WorkflowEngine.Header,
        filename: String,
        externalReportId: ReportId,
        downloadedBy: String,
    ) {
        val parentReportFile = header.reportFile
        trackExistingInputReport(parentReportFile.reportId)
        if (isReportAlreadyTracked(externalReportId)) {
            error(
                "Bug:  attempt to track history of a report ($externalReportId)" +
                    " we've already associated with this action"
            )
        }
        val reportFile = ReportFile()
        reportFile.reportId = externalReportId // child report
        reportFile.receivingOrg = parentReportFile.receivingOrg
        reportFile.receivingOrgSvc = parentReportFile.receivingOrgSvc
        reportFile.schemaName = parentReportFile.schemaName
        reportFile.schemaTopic = parentReportFile.schemaTopic
        reportFile.externalName = filename
        reportFile.transportParams = "{ \"reportRequested\": \"${parentReportFile.reportId}\"}"
        reportFile.transportResult = "{ \"downloadedBy\": \"$downloadedBy\"}"
        reportFile.bodyUrl = null // this entry represents an external file, not a blob.
        reportFile.bodyFormat = parentReportFile.bodyFormat
        reportFile.blobDigest = null // no blob
        reportFile.itemCount = parentReportFile.itemCount
        reportFile.downloadedBy = downloadedBy
        reportsOut[reportFile.reportId] = reportFile
    }

    private fun trackItemLineages(report: Report) {
        // sanity checks
        if (report.itemLineages == null) error("Cannot create lineage For report ${report.id}: missing ItemLineage")
        if (report.itemLineages!!.size != report.itemCount) {
            error(
                "Report ${report.id} should have ${report.itemCount} lineage items" +
                    " but instead has ${report.itemLineages!!.size} lineage items"
            )
        }
        trackItemLineages(report.itemLineages)
    }

    fun trackItemLineages(itemLineages: List<ItemLineage>?) {
        if (itemLineages == null) return
        this.itemLineages.addAll(itemLineages)
    }

    /**
     * Save the history about this action and related reports
     */
    fun saveToDb(txn: Configuration) {
        insertAll(txn)
    }

    fun queueMessages(workflowEngine: WorkflowEngine) {
        messages.forEach { event ->
            queueMessage(event, workflowEngine)
        }
    }

    private fun queueMessage(event: Event, workflowEngine: WorkflowEngine) {
        workflowEngine.queue.sendMessage(event)
        context?.logger?.info("Queued event: ${event.toQueueMessage()}")
    }

    private fun insertAll(txn: Configuration) {
        action.actionId = insertAction(txn)
        reportsReceived.values.forEach { it.actionId = action.actionId }
        reportsOut.values.forEach { it.actionId = action.actionId }
        insertReports(txn)
        generateReportLineages(action.actionId)
        insertReportLineages(txn)
        insertItemLineages(itemLineages, txn)
    }

    /**
     * Returns the action_id PK of the newly inserted ACTION.
     */
    private fun insertAction(txn: Configuration): Long {
        val actionRecord = DSL.using(txn).newRecord(ACTION, action)
        actionRecord.store()
        val actionId = actionRecord.actionId
        context?.logger?.info("Saved to ACTION: ${action.actionName}, id=$actionId")
        return actionId
    }

    private fun insertReports(txn: Configuration) {
        reportsReceived.values.forEach {
            insertReportFile(it, txn)
        }
        reportsOut.values.forEach {
            if (it.itemCount > 0) {
                insertReportFile(it, txn)
            }
        }
    }

    private fun insertReportFile(reportFile: ReportFile, txn: Configuration) {
        DSL.using(txn).newRecord(REPORT_FILE, reportFile).store()
        val fromInfo =
            if (!reportFile.sendingOrg.isNullOrEmpty())
                "${reportFile.sendingOrg}.${reportFile.sendingOrgClient} --> " else ""
        val toInfo =
            if (!reportFile.receivingOrg.isNullOrEmpty())
                " --> ${reportFile.receivingOrg}.${reportFile.receivingOrgSvc}" else ""
        context?.logger?.info(
            "Saved to REPORT_FILE: ${reportFile.reportId} (${fromInfo}action ${action.actionName}$toInfo)"
        )
    }

    /**
     * Use the detailed item lineage to exactly/correctly generate the report parent/child relationships.
     *
     */
    private fun generateReportLineages(actionId: Long) {
        // Extract the distinct parent/child report pairs from the Item Lineage
        val parentChildReports = itemLineages.map { Pair(it.parentReportId, it.childReportId) }.toSet()
        parentChildReports.forEach {
            reportLineages.add(ReportLineage(null, actionId, it.first, it.second, null))
        }

        // If an action has no children, it has no lineage.
        if (reportsOut.size == 0 && parentChildReports.size == 0) return // no lineage assoc with this action.

        // sanity should prevail, at least in ReportStream, if not in general
        if (reportsOut.size > 0 && parentChildReports.size == 0)
            error("There are child reports (${reportsOut.keys.joinToString(",")}) but no item lineages")
        if (reportsOut.size == 0 && parentChildReports.size > 0)
            error("There are item lineages (${parentChildReports.joinToString(",")}) but no child reports")
        // compare the set of reportIds from the item lineage vs the set from report lineage.  Should be identical.
        val parentReports = parentChildReports.map { it.first }.toSet()
        val childReports = parentChildReports.map { it.second }.toSet()
        var parentReports2 = mutableSetOf<ReportId>()
        parentReports2.addAll(reportsReceived.keys)
        parentReports2.addAll(reportsIn.keys)
        val childReports2 = reportsOut.filterValues { it.itemCount > 0 }.keys
        if (!parentReports.equals(parentReports2)) {
            error(
                "parent reports from items (${parentReports.joinToString(",")}) != from reports" +
                    "(${parentReports2.joinToString(",")})"
            )
        }
        if (!childReports.equals(childReports2)) {
            error(
                "child reports from items (${childReports.joinToString(",")} != from reports" +
                    "(${childReports2.joinToString(",")})"
            )
        }
        context?.logger?.info("There are ${reportLineages.size} parent->child report-level relationships")
    }

    private fun insertReportLineages(txn: Configuration) {
        reportLineages.forEach {
            insertReportLineage(it, txn)
        }
    }

    private fun insertReportLineage(lineage: ReportLineage, txn: Configuration) {
        DSL.using(txn).newRecord(REPORT_LINEAGE, lineage).store()
        context?.logger?.info(
            "Report ${lineage.parentReportId} is a parent of child report ${lineage.childReportId}"
        )
    }

    private fun insertItemLineages(itemLineages: Set<ItemLineage>, txn: Configuration) {
        DSL.using(txn)
            .batchInsert(
                itemLineages.map { il ->
                    ItemLineageRecord().also { record ->
                        record.parentReportId = il.parentReportId
                        record.parentIndex = il.parentIndex
                        record.childReportId = il.childReportId
                        record.childIndex = il.childIndex
                        record.trackingId = il.trackingId
                    }
                }
            )
            .execute()

        context?.logger?.info(
            "Inserted ${itemLineages.size} " +
                "Item lineages into db for action ${action.actionId}: ${action.actionName}"
        )
    }

    private fun insertItemLineage(itemLineage: ItemLineage, txn: Configuration) {
        DSL.using(txn).newRecord(ITEM_LINEAGE, itemLineage).store()
    }

    // Used as temp storage by the json generator, below.
    private data class DestinationData(
        val orgReceiver: Receiver,
        val organization: Organization,
        var count: Int,
        val sendingAt: OffsetDateTime? = null,
    )

    /**
     * Generate nice json describing the destinations, suitable for returning to a Hub client.
     * Most of the ugliness here is the attempt to not print every 1-entry report, but combine and summarize them.
     *
     * This works by side-effect on jsonGen.
     */
    fun prettyPrintDestinationsJson(
        jsonGen: JsonGenerator,
        settings: SettingsProvider,
        reportOptions: ReportFunction.Options
    ) {
        var destinationCounter = 0
        jsonGen.writeArrayFieldStart("destinations")
        if (filteredOutReports.isNotEmpty()) {
            filteredOutReports.forEach { (_, reportFile) ->
                val fullname = reportFile.receivingOrg + "." + reportFile.receivingOrgSvc
                val (organization, orgReceiver) = settings.findOrganizationAndReceiver(fullname) ?: return@forEach
                prettyPrintDestinationJson(
                    jsonGen,
                    orgReceiver,
                    organization,
                    reportFile.nextActionAt,
                    reportFile.itemCount,
                    reportOptions,
                    reportFile.reportId
                )
                destinationCounter++
            }
        }
        if (reportsOut.isNotEmpty()) {
            // Avoid clutter.  Combine reports with one Item, and print combined count.
            var singles = mutableMapOf<String, DestinationData>()
            reportsOut.forEach { (_, reportFile) ->
                val fullname = reportFile.receivingOrg + "." + reportFile.receivingOrgSvc
                val (organization, orgReceiver) = settings.findOrganizationAndReceiver(fullname) ?: return@forEach
                if (reportFile.itemCount == 1) {
                    var previous =
                        singles.putIfAbsent(
                            fullname, DestinationData(orgReceiver, organization, 1, reportFile.nextActionAt)
                        )
                    if (previous != null) previous.count++
                } else {
                    prettyPrintDestinationJson(
                        jsonGen,
                        orgReceiver,
                        organization,
                        reportFile.nextActionAt,
                        reportFile.itemCount,
                        reportOptions,
                        reportFile.reportId
                    )
                    destinationCounter++
                }
            }
            singles.forEach { (_, destData) ->
                prettyPrintDestinationJson(
                    jsonGen,
                    destData.orgReceiver,
                    destData.organization,
                    destData.sendingAt,
                    destData.count,
                    reportOptions
                )
                destinationCounter++
            }
        }
        jsonGen.writeEndArray()
        jsonGen.writeNumberField("destinationCount", destinationCounter)
    }

    fun prettyPrintDestinationJson(
        jsonGen: JsonGenerator,
        orgReceiver: Receiver,
        organization: Organization,
        sendingAt: OffsetDateTime?,
        countToPrint: Int,
        reportOptions: ReportFunction.Options,
        reportId: ReportId? = null
    ) {
        jsonGen.writeStartObject()
        // jsonGen.writeStringField("id", reportFile.reportId.toString())   // TMI?
        jsonGen.writeStringField("organization", organization.description)
        jsonGen.writeStringField("organization_id", orgReceiver.organizationName)
        jsonGen.writeStringField("service", orgReceiver.name)

        reportId?.let {
            if (filteredReportRows.contains(it)) {
                jsonGen.writeArrayFieldStart("filteredReportRows")
                filteredReportRows.getValue(it).forEach {
                    jsonGen.writeString(it.toString())
                }
                jsonGen.writeEndArray()
            }
        }

        jsonGen.writeStringField(
            "sending_at",
            when {
                reportOptions == ReportFunction.Options.SkipSend -> {
                    "never - skipSend specified"
                }
                countToPrint == 0 -> {
                    "never - all items filtered out"
                }
                sendingAt == null -> {
                    "immediately"
                }
                else -> {
                    "$sendingAt"
                }
            }
        )

        jsonGen.writeNumberField("itemCount", countToPrint)
        jsonGen.writeEndObject()
    }

    companion object {

        // TODO: Deprecated. Delete.  WorkflowEngine.handleRecieverEvent pulls in each report individually.
        fun fetchReportFilesForReceiver(
            nextAction: TaskAction,
            at: OffsetDateTime?,
            receiver: Receiver,
            limit: Int,
            ctx: DSLContext,
        ): Map<ReportId, ReportFile> {
            val cond = if (at == null) {
                Tables.REPORT_FILE.RECEIVING_ORG.eq(receiver.organizationName)
                    .and(Tables.REPORT_FILE.RECEIVING_ORG_SVC.eq(receiver.name))
                    .and(Tables.REPORT_FILE.NEXT_ACTION.eq(nextAction))
            } else {
                Tables.REPORT_FILE.RECEIVING_ORG.eq(receiver.organizationName)
                    .and(Tables.REPORT_FILE.RECEIVING_ORG_SVC.eq(receiver.name))
                    .and(Tables.REPORT_FILE.NEXT_ACTION.eq(nextAction))
                    .and(Tables.REPORT_FILE.NEXT_ACTION_AT.eq(at))
            }
            return ctx
                .selectFrom(Tables.REPORT_FILE)
                .where(cond)
                .limit(limit)
                .fetch()
                .into(ReportFile::class.java).map { (it.reportId as ReportId) to it }.toMap()
        }

        /**
         * Get rid of this once we have moved away from the old Task table.  In the meantime,
         * this is a way of confirming that the new tables are robust.
         */
        fun sanityCheckReports(
            tasks: List<Task>?,
            reportFiles: Map<ReportId, ReportFile>?,
            failOnError: Boolean = false
        ) {
            var msg: String = ""
            if (tasks == null) {
                msg = "headers is null"
            } else {
                if (reportFiles == null) {
                    msg = "reportFiles is null"
                } else {
                    if (tasks.size != reportFiles.size) {
                        msg = "Different report_file count: Got ${tasks.size} TASKS," +
                            " but ${reportFiles.size} reportFiles.  " +
                            "*** TASK ids: ${tasks.map{ it.reportId}.toSortedSet().joinToString(",")}  " +
                            "*** REPORT_FILE ids:${reportFiles.map { it.key }.toSortedSet().joinToString(",")}"
                    } else {
                        tasks.forEach {
                            sanityCheckReport(it, reportFiles.get(it.reportId), failOnError)
                        }
                    }
                }
            }
            if (msg.isNotEmpty()) {
                if (failOnError) {
                    error("*** Sanity check comparing old Headers list to new ReportFile list FAILED:  $msg")
                } else {
                    println(
                        "************ FAILURE: sanity check comparing old Headers " +
                            "list to new ReportFiles list FAILED:  $msg\""
                    )
                }
            }
        }

        /**
         * Get rid of this once we have moved away from the old Task table.  In the meantime,
         * this is a way of confirming that the new tables are robust.
         */
        fun sanityCheckReport(task: Task?, reportFile: ReportFile?, failOnError: Boolean = false) {
            var msg: String = ""
            if (task == null) {
                msg = "header is null"
            } else {
                if (reportFile == null) {
                    msg = "reportFile is null - no matching report was retreived with ${task.reportId}"
                } else {
                    if (task.bodyFormat != reportFile.bodyFormat) {
                        msg = "header.bodyFormat = ${task.bodyFormat}, " +
                            "but reportFile.bodyFormat= ${reportFile.bodyFormat}, "
                    }
                    if (task.bodyUrl != reportFile.bodyUrl) {
                        msg += "header.bodyUrl = ${task.bodyUrl}, but reportFile.bodyFormat= ${reportFile.bodyUrl}, "
                    }
                    if (task.itemCount != reportFile.itemCount) {
                        msg += "header.itemCount = ${task.itemCount}, " +
                            "but reportFile.itemCount= ${reportFile.itemCount}, "
                    }
                    if (task.receiverName != (reportFile.receivingOrg + "." + reportFile.receivingOrgSvc)) {
                        msg += "header.receiverName = ${task.receiverName}, but reportFile has " +
                            "${reportFile.receivingOrg + "." + reportFile.receivingOrgSvc}"
                    }
                    if (task.reportId != reportFile.reportId) {
                        msg += "header.reportId = ${task.reportId}, but reportFile.reportId= ${reportFile.reportId}, "
                    }
                }
            }
            if (msg.isNotEmpty()) {
                if (failOnError) {
                    error("*** Sanity check comparing old Header info and new ReportFile info FAILED:  $msg")
                } else {
                    System.out.println(
                        "************ FAILURE: sanity check comparing " +
                            "old Header info and new ReportFile info FAILED:  $msg\""
                    )
                }
            }
        }
    }
}