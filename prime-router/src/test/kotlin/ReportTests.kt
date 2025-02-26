package gov.cdc.prime.router

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.fail

class ReportTests {
    private val metadata = Metadata.getInstance()

    val rcvr = Receiver("name", "org", "topic", CustomerStatus.INACTIVE, "schema", Report.Format.CSV)

    @Test
    fun `test merge`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a"), Element("b")))
        val report1 = Report(one, listOf(listOf("1", "2"), listOf("3", "4")), source = TestSource)
        val report2 = Report(one, listOf(listOf("5", "6"), listOf("7", "8")), source = TestSource)
        val mergedReport = Report.merge(listOf(report1, report2))
        assertThat(mergedReport.itemCount).isEqualTo(4)
        assertThat(report1.itemCount).isEqualTo(2)
        assertThat(mergedReport.getString(3, "b")).isEqualTo("8")
        assertThat(mergedReport.sources.size).isEqualTo(2)
        assertThat(report1.id).isEqualTo((mergedReport.sources[0] as ReportSource).id)
    }

    @Test
    fun `test filter`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a"), Element("b")))
        val metadata = Metadata(schema = one)
        val jurisdictionalFilter = metadata.findJurisdictionalFilter("matches") ?: fail("cannot find filter")
        val report1 = Report(one, listOf(listOf("1", "2"), listOf("3", "4")), source = TestSource)
        assertThat(report1.itemCount).isEqualTo(2)
        val filteredReport = report1.filter(listOf(Pair(jurisdictionalFilter, listOf("a", "1"))), rcvr, false)
        assertThat(filteredReport.schema).isEqualTo(one)
        assertThat(filteredReport.itemCount).isEqualTo(1)
        assertThat(filteredReport.getString(0, "b")).isEqualTo("2")
        assertThat(filteredReport.sources.size).isEqualTo(1)
    }

    @Test
    fun `test multiarg matches filter`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a"), Element("b")))
        val metadata = Metadata(schema = one)
        val jurisdictionalFilter = metadata.findJurisdictionalFilter("matches") ?: fail("cannot find filter")
        // each sublist is a row.
        val report1 = Report(one, listOf(listOf("row1_a", "row1_b"), listOf("row2_a", "row2_b")), source = TestSource)
        assertThat(2).isEqualTo(report1.itemCount)
        val filteredReportA = report1.filter(
            listOf(Pair(jurisdictionalFilter, listOf("a", "row1.*", "row2_a"))), rcvr, false
        )
        assertThat(filteredReportA.itemCount).isEqualTo(2)
        assertThat(filteredReportA.getString(0, "b")).isEqualTo("row1_b")
        assertThat(filteredReportA.getString(1, "b")).isEqualTo("row2_b")

        val filteredReportB = report1.filter(
            listOf(Pair(jurisdictionalFilter, listOf("a", "row.*"))), rcvr, false
        )
        assertThat(filteredReportA.itemCount).isEqualTo(2)
        assertThat(filteredReportB.getString(0, "b")).isEqualTo("row1_b")
        assertThat(filteredReportB.getString(1, "b")).isEqualTo("row2_b")

        val filteredReportC = report1.filter(
            listOf(Pair(jurisdictionalFilter, listOf("a", "row1_a", "foo", "bar", "baz"))), rcvr, false
        )
        assertThat(filteredReportC.itemCount).isEqualTo(1)
        assertThat(filteredReportC.getString(0, "b")).isEqualTo("row1_b")

        val filteredReportD = report1.filter(
            listOf(Pair(jurisdictionalFilter, listOf("a", "argle", "bargle"))), rcvr, false
        )
        assertThat(filteredReportD.itemCount).isEqualTo(0)
    }

    @Test
    fun `test isEmpty`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a"), Element("b")))
        val emptyReport = Report(one, emptyList(), source = TestSource)
        assertThat(emptyReport.isEmpty()).isEqualTo(true)
        val report1 = Report(one, listOf(listOf("1", "2")), source = TestSource)
        assertThat(report1.isEmpty()).isEqualTo(false)
    }

    @Test
    fun `test create with list`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a"), Element("b")))
        val report1 = Report(one, listOf(listOf("1", "2")), TestSource)
        assertThat(report1.schema).isEqualTo(one)
        assertThat(report1.itemCount).isEqualTo(1)
        assertThat(TestSource).isEqualTo(report1.sources[0] as TestSource)
    }

    @Test
    fun `test applyMapping`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a"), Element("b")))
        val two = Schema(name = "two", topic = "test", elements = listOf(Element("b")))
        val metadata = Metadata()
        metadata.loadSchemas(one, two)

        val oneReport = Report(schema = one, values = listOf(listOf("a1", "b1"), listOf("a2", "b2")), TestSource)
        assertThat(oneReport.itemCount).isEqualTo(2)
        val mappingOneToTwo = Translator(metadata, FileSettings())
            .buildMapping(fromSchema = one, toSchema = two, defaultValues = emptyMap())

        val twoTable = oneReport.applyMapping(mappingOneToTwo)
        assertThat(twoTable.itemCount).isEqualTo(2)
        assertThat(twoTable.getString(1, "b")).isEqualTo("b2")
    }

    @Test
    fun `test applyMapping with default`() {
        val one = Schema(
            name = "one",
            topic = "test",
            elements = listOf(Element("a", default = "~"), Element("b"))
        )
        val two = Schema(name = "two", topic = "test", elements = listOf(Element("b")))
        val metadata = Metadata()
        metadata.loadSchemas(one, two)

        val twoReport = Report(schema = two, values = listOf(listOf("b1"), listOf("b2")), source = TestSource)
        assertThat(twoReport.itemCount).isEqualTo(2)
        val mappingTwoToOne = Translator(metadata, FileSettings())
            .buildMapping(fromSchema = two, toSchema = one, defaultValues = emptyMap())

        val oneReport = twoReport.applyMapping(mappingTwoToOne)
        assertThat(oneReport.itemCount).isEqualTo(2)
        assertThat(oneReport.getString(0, "a")).isEqualTo("~")
        assertThat(oneReport.getString(1, "b")).isEqualTo("b2")
    }

    @Test
    fun `test deidentify`() {
        val one = Schema(
            name = "one",
            topic = "test",
            elements = listOf(Element("a", pii = true), Element("b"))
        )

        val oneReport = Report(
            schema = one,
            values = listOf(listOf("a1", "b1"), listOf("a2", "b2")),
            source = TestSource
        )

        val oneDeidentified = oneReport.deidentify()
        assertThat(oneDeidentified.itemCount).isEqualTo(2)
        assertThat(oneDeidentified.getString(0, "a")).isEqualTo("")
        assertThat(oneDeidentified.getString(0, "b")).isEqualTo("b1")
    }

    @Test
    fun `test patient age validation`() {

        /**
         * Create table's header
         */
        val oneWithAge = Schema(
            name = "one", topic = "test",
            elements = listOf(
                Element("message_id"), Element("patient_age"),
                Element("specimen_collection_date_time"), Element("patient_dob")
            )
        )

        /**
         * Add Rows values to the table
         */
        val oneReport = Report(
            schema = oneWithAge,
            values = listOf(
                listOf("0", "100", "202110300809", "30300102"), // Good age, ... don't care -> patient_age=100
                // Bad age, good collect date, BAD DOB -> patient_age=null
                listOf("1", ")@*", "202110300809-0501", "30300101"),
                // Bad age, bad collect date, good dob -> patient_age=null
                listOf("2", "_", "202110300809", "20190101"),
                // Good age, bad collect date, bad dob -> patient_age=20
                listOf("3", "20", "adfadf", "!@!*@(7"),
                // Bad age, good collect date, good dob -> patient_age=2
                listOf("4", "0", "202110300809-0500", "20190101"),
                // Bad age, good collect data, good dob -> patient_age=10
                listOf("5", "-5", "202110300809-0502", "20111029"),
                // Good age, ... don't care -> patient_age = 40
                listOf("6", "40", "asajh", "20190101"),
                // Good age is blank, -> patient_age=null
                listOf("7", "", "asajh", "20190101"),
            ),
            TestSource
        )

        val covidResultMetadata = oneReport.getDeidentifiedResultMetaData()
        assertThat(covidResultMetadata).isNotNull()
        assertThat(covidResultMetadata.get(0).patientAge).isEqualTo("100")
        assertThat(covidResultMetadata.get(1).patientAge).isNull()
        assertThat(covidResultMetadata.get(2).patientAge).isNull()
        assertThat(covidResultMetadata.get(3).patientAge).isEqualTo("20")
        assertThat(covidResultMetadata.get(4).patientAge).isEqualTo("2")
        assertThat(covidResultMetadata.get(5).patientAge).isEqualTo("10")
        assertThat(covidResultMetadata.get(6).patientAge).isEqualTo("40")
        assertThat(covidResultMetadata.get(7).patientAge).isNull()

        /**
         * Test table with out patient_age
         */
        val twoWithoutAge = Schema(
            name = "one", topic = "test",
            elements = listOf(
                Element("message_id"), Element("specimen_collection_date_time"),
                Element("patient_dob")
            )
        )

        /**
         * Add Rows values to the table
         */
        val twoReport = Report(
            schema = twoWithoutAge,
            values = listOf(
                listOf("0", "202110300809", "30300102"), // Bad speciment collection date -> patient_age=null
                listOf("1", "202110300809-0501", "30300101"), // good collect date, BAD DOB -> patient_age=null
                listOf("2", "202110300809-0500", "20190101")
            ), // Bad age, good collect date, good dob -> patient_age=2
            TestSource
        )

        val covidResultMetadata2 = twoReport.getDeidentifiedResultMetaData()
        assertThat(covidResultMetadata2).isNotNull()
        assertThat(covidResultMetadata2.get(0).patientAge).isNull()
        assertThat(covidResultMetadata2.get(1).patientAge).isNull()
        assertThat(covidResultMetadata2.get(2).patientAge).isEqualTo("2")
    }

    // Tests for Item lineage
    @Test
    fun `test merge item lineage`() {
        val schema = Schema(name = "one", topic = "test", elements = listOf(Element("a")), trackingElement = "a")
        // each sublist is a row.
        val report1 = Report(schema, listOf(listOf("rep1_row1_a"), listOf("rep1_row2_a")), source = TestSource)
        val report2 = Report(schema, listOf(listOf("rep2_row1_a"), listOf("rep2_row2_a")), source = TestSource)

        val merged = Report.merge(listOf(report1, report2))

        assertThat(merged.itemLineages!!.size).isEqualTo(4)
        val firstLineage = merged.itemLineages!![0]
        assertThat(firstLineage.parentReportId).isEqualTo(report1.id)
        assertThat(firstLineage.parentIndex).isEqualTo(0)
        assertThat(firstLineage.childReportId).isEqualTo(merged.id)
        assertThat(firstLineage.childIndex).isEqualTo(0)
        assertThat(firstLineage.trackingId).isEqualTo("rep1_row1_a")

        val lastLineage = merged.itemLineages!![3]
        assertThat(lastLineage.parentReportId).isEqualTo(report2.id)
        assertThat(lastLineage.parentIndex).isEqualTo(1)
        assertThat(lastLineage.childReportId).isEqualTo(merged.id)
        assertThat(lastLineage.childIndex).isEqualTo(3)
        assertThat(lastLineage.trackingId).isEqualTo("rep2_row2_a")
    }

    @Test
    fun `test split item lineage`() {
        val schema = Schema(name = "one", topic = "test", elements = listOf(Element("a")), trackingElement = "a")
        // each sublist is a row.
        val report1 = Report(schema, listOf(listOf("rep1_row1_a"), listOf("rep1_row2_a")), source = TestSource)

        val reports = report1.split()

        assertThat(reports.size).isEqualTo(2)
        assertThat(reports[0].itemLineages!!.size).isEqualTo(1)
        assertThat(reports[1].itemLineages!!.size).isEqualTo(1)

        val firstLineage = reports[0].itemLineages!![0]
        assertThat(firstLineage.parentReportId).isEqualTo(report1.id)
        assertThat(firstLineage.parentIndex).isEqualTo(0)
        assertThat(firstLineage.childReportId).isEqualTo(reports[0].id)
        assertThat(firstLineage.childIndex).isEqualTo(0)
        assertThat(firstLineage.trackingId).isEqualTo("rep1_row1_a")

        val secondLineage = reports[1].itemLineages!![0]
        assertThat(secondLineage.parentReportId).isEqualTo(report1.id)
        assertThat(secondLineage.parentIndex).isEqualTo(1)
        assertThat(secondLineage.childReportId).isEqualTo(reports[1].id)
        assertThat(secondLineage.childIndex).isEqualTo(0)
        assertThat(secondLineage.trackingId).isEqualTo("rep1_row2_a")
    }

    @Test
    fun `test item lineage after jurisdictional filter`() {
        val schema = Schema(name = "one", topic = "test", elements = listOf(Element("a")), trackingElement = "a")
        val metadata = Metadata(schema = schema)
        val jurisdictionalFilter = metadata.findJurisdictionalFilter("matches") ?: fail("cannot find filter")
        // each sublist is a row.
        val report1 = Report(schema, listOf(listOf("rep1_row1_a"), listOf("rep1_row2_a")), source = TestSource)

        val filteredReport = report1.filter(listOf(Pair(jurisdictionalFilter, listOf("a", "rep1_row2_a"))), rcvr, false)

        val lineage = filteredReport.itemLineages!!
        assertThat(lineage.size).isEqualTo(1)
        assertThat(lineage[0].parentReportId).isEqualTo(report1.id)
        assertThat(lineage[0].parentIndex).isEqualTo(1)
        assertThat(lineage[0].childReportId).isEqualTo(filteredReport.id)
        assertThat(lineage[0].childIndex).isEqualTo(0)
        assertThat(lineage[0].trackingId).isEqualTo("rep1_row2_a")
    }

    @Test
    fun `test merge then split`() {
        val schema = Schema(name = "one", topic = "test", elements = listOf(Element("a")), trackingElement = "a")
        // each sublist is a row.
        val report1 = Report(schema, listOf(listOf("rep1_row1_a"), listOf("rep1_row2_a")), source = TestSource)
        val report2 = Report(schema, listOf(listOf("rep2_row1_a"), listOf("rep2_row2_a")), source = TestSource)

        val merged = Report.merge(listOf(report1, report2))
        val reports = merged.split()

        assertThat(reports.size).isEqualTo(4)
        assertThat(reports[0].itemLineages!!.size).isEqualTo(1)
        assertThat(reports[3].itemLineages!!.size).isEqualTo(1)

        val firstLineage = reports[0].itemLineages!![0]
        assertThat(firstLineage.parentReportId).isEqualTo(report1.id)
        assertThat(firstLineage.parentIndex).isEqualTo(0)
        assertThat(firstLineage.childReportId).isEqualTo(reports[0].id)
        assertThat(firstLineage.childIndex).isEqualTo(0)
        assertThat(firstLineage.trackingId).isEqualTo("rep1_row1_a")

        val fourthLineage = reports[3].itemLineages!![0]
        assertThat(fourthLineage.parentReportId).isEqualTo(report2.id)
        assertThat(fourthLineage.parentIndex).isEqualTo(1)
        assertThat(fourthLineage.childReportId).isEqualTo(reports[3].id)
        assertThat(fourthLineage.childIndex).isEqualTo(0)
        assertThat(fourthLineage.trackingId).isEqualTo("rep2_row2_a")
    }

    @Test
    fun `test lineage insanity`() {
        val schema = Schema(name = "one", topic = "test", elements = listOf(Element("a")), trackingElement = "a")
        // each sublist is a row.
        val report1 = Report(schema, listOf(listOf("bbb"), listOf("aaa"), listOf("aaa")), source = TestSource)
        val metadata = Metadata(schema = schema)
        val jurisdictionalFilter = metadata.findJurisdictionalFilter("matches") ?: fail("cannot find filter")

        // split, merge, split, merge, copy, copy, then filter.
        val reports1 = report1.split()
        val merge1 = Report.merge(reports1)
        val reports2 = merge1.split()
        val merge2 = Report.merge(reports2)
        val copy1 = merge2.copy()
        val copy2 = copy1.copy()
        val filteredReport = copy2.filter(listOf(Pair(jurisdictionalFilter, listOf("a", "aaa"))), rcvr, false)

        val lineage = filteredReport.itemLineages!!
        assertThat(lineage.size).isEqualTo(2)

        assertThat(lineage[0].parentReportId).isEqualTo(report1.id)
        assertThat(lineage[0].parentIndex).isEqualTo(1)
        assertThat(lineage[0].childReportId).isEqualTo(filteredReport.id)
        assertThat(lineage[0].childIndex).isEqualTo(0)
        assertThat(lineage[0].trackingId).isEqualTo("aaa")

        assertThat(lineage[1].parentReportId).isEqualTo(report1.id)
        assertThat(lineage[1].parentIndex).isEqualTo(2)
        assertThat(lineage[1].childReportId).isEqualTo(filteredReport.id)
        assertThat(lineage[1].childIndex).isEqualTo(1)
        assertThat(lineage[1].trackingId).isEqualTo("aaa")
    }

    @Test
    fun `test synthesize data with empty strategy map`() {
        // arrange
        val schema = Schema(
            name = "test",
            topic = "test",
            elements = listOf(
                Element("last_name"), Element("first_name")
            )
        )
        val report = Report(
            schema = schema,
            values = listOf(listOf("smith", "sarah"), listOf("jones", "mary"), listOf("white", "roberta")),
            source = TestSource
        )
        // act
        val synthesizedReport = report.synthesizeData(metadata = metadata)
        // assert
        assertThat(synthesizedReport.itemCount).isEqualTo(3)
        assertThat(synthesizedReport.getString(0, "last_name")).isEqualTo("smith")
        assertThat(synthesizedReport.getString(1, "last_name")).isEqualTo("jones")
        assertThat(synthesizedReport.getString(2, "last_name")).isEqualTo("white")
        assertThat(synthesizedReport.getString(0, "first_name")).isEqualTo("sarah")
        assertThat(synthesizedReport.getString(1, "first_name")).isEqualTo("mary")
        assertThat(synthesizedReport.getString(2, "first_name")).isEqualTo("roberta")
    }

    @Test
    fun `test synthesize data with pass through strategy map`() {
        // arrange
        val schema = Schema(
            name = "test",
            topic = "test",
            elements = listOf(
                Element("last_name"), Element("first_name")
            )
        )
        val report = Report(
            schema = schema,
            values = listOf(listOf("smith", "sarah"), listOf("jones", "mary"), listOf("white", "roberta")),
            source = TestSource
        )
        val strategies = mapOf(
            "last_name" to Report.SynthesizeStrategy.PASSTHROUGH,
            "first_name" to Report.SynthesizeStrategy.PASSTHROUGH
        )
        // act
        val synthesizedReport = report.synthesizeData(strategies, metadata = metadata)
        // assert
        assertThat(synthesizedReport.itemCount).isEqualTo(3)
        assertThat(synthesizedReport.getString(0, "last_name")).isEqualTo("smith")
        assertThat(synthesizedReport.getString(1, "last_name")).isEqualTo("jones")
        assertThat(synthesizedReport.getString(2, "last_name")).isEqualTo("white")
        assertThat(synthesizedReport.getString(0, "first_name")).isEqualTo("sarah")
        assertThat(synthesizedReport.getString(1, "first_name")).isEqualTo("mary")
        assertThat(synthesizedReport.getString(2, "first_name")).isEqualTo("roberta")
    }

    @Test
    fun `test synthesize data with blank strategy`() {
        // arrange
        val schema = Schema(
            name = "test",
            topic = "test",
            elements = listOf(
                Element("last_name"), Element("first_name"), Element("ssn")
            )
        )
        val report = Report(
            schema = schema,
            values = listOf(
                listOf("smith", "sarah", "000000000"),
                listOf("jones", "mary", "000000000"),
                listOf("white", "roberta", "000000000"),
            ),
            source = TestSource
        )
        val strategies = mapOf(
            "last_name" to Report.SynthesizeStrategy.PASSTHROUGH,
            "first_name" to Report.SynthesizeStrategy.PASSTHROUGH,
            "ssn" to Report.SynthesizeStrategy.BLANK,
        )
        // act
        val synthesizedReport = report.synthesizeData(strategies, metadata = metadata)
        // assert
        assertThat(synthesizedReport.itemCount).isEqualTo(3)
        assertThat(synthesizedReport.getString(0, "last_name")).isEqualTo("smith")
        assertThat(synthesizedReport.getString(1, "last_name")).isEqualTo("jones")
        assertThat(synthesizedReport.getString(2, "last_name")).isEqualTo("white")
        assertThat(synthesizedReport.getString(0, "first_name")).isEqualTo("sarah")
        assertThat(synthesizedReport.getString(1, "first_name")).isEqualTo("mary")
        assertThat(synthesizedReport.getString(2, "first_name")).isEqualTo("roberta")
        assertThat(synthesizedReport.getString(0, "ssn")).isEqualTo("")
        assertThat(synthesizedReport.getString(1, "ssn")).isEqualTo("")
        assertThat(synthesizedReport.getString(2, "ssn")).isEqualTo("")
    }

    // ignoring this test for now because shuffling is non-deterministic
    @Test
    @Ignore
    fun `test synthesize data with shuffle strategy`() {
        // arrange
        val schema = Schema(
            name = "test",
            topic = "test",
            elements = listOf(
                Element("last_name"), Element("first_name"),
            )
        )
        val report = Report(
            schema = schema,
            values = listOf(
                listOf("smith", "sarah"),
                listOf("jones", "mary"),
                listOf("white", "roberta"),
                listOf("stock", "julie"),
                listOf("chang", "emily"),
                listOf("rodriguez", "anna"),
            ),
            source = TestSource
        )
        val strategies = mapOf(
            "last_name" to Report.SynthesizeStrategy.SHUFFLE,
            "first_name" to Report.SynthesizeStrategy.SHUFFLE,
        )
        // act
        val synthesizedReport = report.synthesizeData(strategies, metadata = metadata)
        // assert
        assertThat(synthesizedReport.getString(0, "last_name")).isNotEqualTo("smith")
        assertThat(synthesizedReport.getString(1, "last_name")).isNotEqualTo("jones")
        assertThat(synthesizedReport.getString(2, "last_name")).isNotEqualTo("white")
        assertThat(synthesizedReport.getString(0, "first_name")).isNotEqualTo("sarah")
        assertThat(synthesizedReport.getString(1, "first_name")).isNotEqualTo("mary")
        assertThat(synthesizedReport.getString(2, "first_name")).isNotEqualTo("roberta")
    }
}