package gov.cdc.prime.router.serializers

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.model.Segment
import ca.uhn.hl7v2.model.v251.datatype.DR
import ca.uhn.hl7v2.model.v251.datatype.DT
import ca.uhn.hl7v2.model.v251.datatype.DTM
import ca.uhn.hl7v2.model.v251.datatype.TS
import ca.uhn.hl7v2.model.v251.datatype.XTN
import ca.uhn.hl7v2.model.v251.message.ORU_R01
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import ca.uhn.hl7v2.util.Terser
import gov.cdc.prime.router.Element
import gov.cdc.prime.router.FileSettings
import gov.cdc.prime.router.FileSource
import gov.cdc.prime.router.Hl7Configuration
import gov.cdc.prime.router.Metadata
import gov.cdc.prime.router.Receiver
import gov.cdc.prime.router.Report
import gov.cdc.prime.router.Schema
import gov.cdc.prime.router.TestSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.verify
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Hl7SerializerTests {
    private val hl7TestFileDir = "./src/test/hl7_test_files/"
    private val hl7SchemaName = "hl7/test-covid-19"
    private val testReport: Report
    private val context = DefaultHapiContext()
    private val mcf = CanonicalModelClassFactory("2.5.1")
    private val serializer: Hl7Serializer
    private val csvSerializer: CsvSerializer
    private val covid19Schema: Schema
    private val sampleHl7Message: String
    private val sampleHl7MessageWithRepeats: String
    private val metadata = Metadata.getInstance()

    init {
        val settings = FileSettings("./settings")
        val inputStream = File("./src/test/unit_test_files/fake-pdi-covid-19.csv").inputStream()
        covid19Schema = metadata.findSchema(hl7SchemaName) ?: fail("Could not find target schema")
        csvSerializer = CsvSerializer(metadata)
        serializer = Hl7Serializer(metadata, settings)
        testReport = csvSerializer.readExternal("primedatainput/pdi-covid-19", inputStream, TestSource).report ?: fail()
        sampleHl7Message = """MSH|^~\&|CDC PRIME - Atlanta, Georgia (Dekalb)^2.16.840.1.114222.4.1.237821^ISO|Avante at Ormond Beach^10D0876999^CLIA|||20210210170737||ORU^R01^ORU_R01|371784|P|2.5.1|||NE|NE|USA||||PHLabReportNoAck^ELR_Receiver^2.16.840.1.113883.9.11^ISO
SFT|Centers for Disease Control and Prevention|0.1-SNAPSHOT|PRIME ReportStream|0.1-SNAPSHOT||20210210
PID|1||2a14112c-ece1-4f82-915c-7b3a8d152eda^^^Avante at Ormond Beach^PI||Buckridge^Kareem^Millie^^^^L||19580810|F||2106-3^White^HL70005^^^^2.5.1|688 Leighann Inlet^^South Rodneychester^TX^67071||^PRN^^roscoe.wilkinson@email.com^1^211^2240784|||||||||U^Unknown^HL70189||||||||N
ORC|RE|73a6e9bd-aaec-418e-813a-0ad33366ca85|73a6e9bd-aaec-418e-813a-0ad33366ca85|||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI||^WPN^^^1^386^6825220|20210209||||||Avante at Ormond Beach|170 North King Road^^Ormond Beach^FL^32174^^^^12127|^WPN^^jbrush@avantecenters.com^1^407^7397506|^^^^32174
OBR|1|73a6e9bd-aaec-418e-813a-0ad33366ca85||94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN|||202102090000-0600|202102090000-0600||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI|^WPN^^^1^386^6825220|||||202102090000-0600|||F
OBX|1|CWE|94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN||260415000^Not detected^SCT|||N^Normal (applies to non-numeric results)^HL70078|||F|||202102090000-0600|||CareStart COVID-19 Antigen test_Access Bio, Inc._EUA^^99ELR||202102090000-0600||||Avante at Ormond Beach^^^^^CLIA&2.16.840.1.113883.4.7&ISO^^^^10D0876999^CLIA|170 North King Road^^Ormond Beach^FL^32174^^^^12127
NTE|1|L|This is a comment|RE
OBX|2|CWE|95418-0^Whether patient is employed in a healthcare setting^LN^^^^2.69||Y^Yes^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|3|CWE|95417-2^First test for condition of interest^LN^^^^2.69||Y^Yes^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|4|CWE|95421-4^Resides in a congregate care setting^LN^^^^2.69||N^No^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|5|CWE|95419-8^Has symptoms related to condition of interest^LN^^^^2.69||N^No^HL70136||||||F|||202102090000-0600|||||||||||||||QST
SPM|1|||258500001^Nasopharyngeal swab^SCT||||71836000^Nasopharyngeal structure (body structure)^SCT^^^^2020-09-01|||||||||202102090000-0600^202102090000-0600
NTE|1|L|This is a final comment|RE"""
        sampleHl7MessageWithRepeats = """MSH|^~\&|CDC PRIME - Atlanta, Georgia (Dekalb)^2.16.840.1.114222.4.1.237821^ISO|Avante at Ormond Beach^10D0876999^CLIA|||20210210170737||ORU^R01^ORU_R01|371784|P|2.5.1|||NE|NE|USA||||PHLabReportNoAck^ELR_Receiver^2.16.840.1.113883.9.11^ISO
SFT|Centers for Disease Control and Prevention|0.1-SNAPSHOT|PRIME ReportStream|0.1-SNAPSHOT||20210210
PID|1||2a14112c-ece1-4f82-915c-7b3a8d152eda^^^Avante at Ormond Beach^PI||Buckridge^Kareem^Millie^^^^L||19580810|F||2106-3^White^HL70005^^^^2.5.1|688 Leighann Inlet^^South Rodneychester^TX^67071||^NET^Internet^roscoe.wilkinson@email.com~(211)224-0784^PRN^PH^^1^211^2240784|||||||||U^Unknown^HL70189||||||||N
ORC|RE|73a6e9bd-aaec-418e-813a-0ad33366ca85|73a6e9bd-aaec-418e-813a-0ad33366ca85|||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI||^WPN^^^1^386^6825220|20210209||||||Avante at Ormond Beach|170 North King Road^^Ormond Beach^FL^32174^^^^12127|^WPN^^jbrush@avantecenters.com^1^407^7397506|^^^^32174
OBR|1|73a6e9bd-aaec-418e-813a-0ad33366ca85||94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN|||202102090000-0600|202102090000-0600||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI|^WPN^^^1^386^6825220|||||202102090000-0600|||F
OBX|1|CWE|94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN||260415000^Not detected^SCT|||N^Normal (applies to non-numeric results)^HL70078|||F|||202102090000-0600|||CareStart COVID-19 Antigen test_Access Bio, Inc._EUA^^99ELR||202102090000-0600||||Avante at Ormond Beach^^^^^CLIA&2.16.840.1.113883.4.7&ISO^^^^10D0876999^CLIA|170 North King Road^^Ormond Beach^FL^32174^^^^12127
NTE|1|L|This is a comment|RE
OBX|2|CWE|95418-0^Whether patient is employed in a healthcare setting^LN^^^^2.69||Y^Yes^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|3|CWE|95417-2^First test for condition of interest^LN^^^^2.69||Y^Yes^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|4|CWE|95421-4^Resides in a congregate care setting^LN^^^^2.69||N^No^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|5|CWE|95419-8^Has symptoms related to condition of interest^LN^^^^2.69||N^No^HL70136||||||F|||202102090000-0600|||||||||||||||QST
SPM|1|||258500001^Nasopharyngeal swab^SCT||||71836000^Nasopharyngeal structure (body structure)^SCT^^^^2020-09-01|||||||||202102090000-0600^202102090000-0600
NTE|1|L|This is a final comment|RE"""
    }

    @Test
    fun `Test write batch`() {
        val outputStream = ByteArrayOutputStream()
        serializer.writeBatch(testReport, outputStream)
        val output = outputStream.toString(StandardCharsets.UTF_8)
        assertThat(output).isNotNull()
    }

    @Test
    fun `test write a message`() {
        val output = serializer.createMessage(testReport, 0)
        assertThat(output).isNotNull()
    }

    @Test
    fun `test write a message with Receiver`() {
        val inputStream = File("./src/test/unit_test_files/ak_test_file.csv").inputStream()
        val schema = "primedatainput/pdi-covid-19"

        val hl7Config = mockkClass(Hl7Configuration::class).also {
            every { it.replaceValue }.returns(mapOf("PID-22-3" to "CDCREC,-,testCDCREC", "MSH-9" to "MSH-10"))
            every { it.format }.returns(Report.Format.HL7)
            every { it.useTestProcessingMode }.returns(false)
            every { it.suppressQstForAoe }.returns(false)
            every { it.suppressAoe }.returns(false)
            every { it.suppressHl7Fields }.returns(null)
            every { it.useBlankInsteadOfUnknown }.returns(null)
            every { it.convertTimestampToDateTime }.returns(null)
            every { it.truncateHDNamespaceIds }.returns(false)
            every { it.phoneNumberFormatting }.returns(Hl7Configuration.PhoneNumberFormatting.STANDARD)
            every { it.usePid14ForPatientEmail }.returns(false)
            every { it.reportingFacilityName }.returns(null)
            every { it.reportingFacilityId }.returns(null)
            every { it.reportingFacilityIdType }.returns(null)
            every { it.cliaForOutOfStateTesting }.returns("1234FAKECLIA")
            every { it.useOrderingFacilityName }.returns(Hl7Configuration.OrderingFacilityName.STANDARD)
            every { it.cliaForSender }.returns(mapOf())
        }
        val receiver = mockkClass(Receiver::class).also {
            every { it.translation }.returns(hl7Config)
            every { it.format }.returns(Report.Format.HL7)
            every { it.organizationName }.returns("ca-dph")
        }

        val testReport = csvSerializer.readExternal(schema, inputStream, listOf(TestSource), receiver).report ?: fail()
        val output = serializer.createMessage(testReport, 2)
        assertThat(output).isNotNull()
    }

    @Test
    fun `test reading message from serializer`() {
        // arrange
        val mcf = CanonicalModelClassFactory("2.5.1")
        context.modelClassFactory = mcf
        val parser = context.pipeParser
        // act
        val reg = "[\r\n]".toRegex()
        val cleanedMessage = reg.replace(sampleHl7Message, "\r")
        val hapiMsg = parser.parse(cleanedMessage)
        val terser = Terser(hapiMsg)
        // these messages are of type ORU_R01, so we can cast to that
        // as well, and let's test that while we're here as well
        val oru = hapiMsg as ORU_R01
        // assert
        assertThat(terser.get("/MSH-3-1")).isEqualTo("CDC PRIME - Atlanta, Georgia (Dekalb)")
        assertThat(terser.get("/MSH-3-2")).isEqualTo("2.16.840.1.114222.4.1.237821")
        assertThat(terser.get("/.PID-11-3")).isEqualTo("South Rodneychester")
        // check the oru cast
        assertThat(oru).isNotNull()
        assertThat(oru.patienT_RESULT.patient).isNotNull()
        assertThat(oru.patienT_RESULT.patient.pid).isNotNull()
        println(oru.printStructure())
    }

    @Test
    fun `test reading pid repeats`() {
        // arrange
        context.modelClassFactory = mcf
        val parser = context.pipeParser
        // act
        val reg = "[\r\n]".toRegex()
        val cleanedMessage = reg.replace(sampleHl7MessageWithRepeats, "\r")
        val hapiMsg = parser.parse(cleanedMessage)
        val terser = Terser(hapiMsg)
        // these messages are of type ORU_R01, so we can cast to that
        // as well, and let's test that while we're here as well
        val oru = hapiMsg as ORU_R01
        // ^NET^Internet^roscoe.wilkinson@email.com~(211)224-0784^PRN^PH^^1^211^2240784
        // assert
        assertThat(terser.get("/PATIENT_RESULT/PATIENT/PID-13(0)-2")).isEqualTo("NET")
        assertThat(terser.get("/PATIENT_RESULT/PATIENT/PID-13(0)-3")).isEqualTo("Internet")
        assertThat(terser.get("/PATIENT_RESULT/PATIENT/PID-13(1)-1")).isEqualTo("(211)224-0784")
        assertThat(terser.get("/PATIENT_RESULT/PATIENT/PID-13(1)-2")).isEqualTo("PRN")
        println(terser.get("/PATIENT_RESULT/PATIENT/PID-13(0)"))
        println(terser.get("/PATIENT_RESULT/PATIENT/PID-13(1)"))
        // check the oru cast
        assertThat(oru).isNotNull()
        assertThat(oru.patienT_RESULT.patient).isNotNull()
        assertThat(oru.patienT_RESULT.patient.pid).isNotNull()
        assertThat(oru.patienT_RESULT.patient.pid.phoneNumberHome).isNotNull()
        assertThat(oru.patienT_RESULT.patient.pid.phoneNumberHome).hasSize(2)
        println(oru.patienT_RESULT.patient.pid.phoneNumberHome[0])
        println(oru.printStructure())
    }

    @Test
    fun `test converting hl7 into mapped list of values`() {
        val mappedMessage = serializer.convertMessageToMap(sampleHl7Message, covid19Schema)
        val mappedValues = mappedMessage.row
        println("\ntest converting hl7 into mapped list of values:\n")
        mappedValues.forEach {
            println("${it.key}: ${it.value.joinToString()}")
        }
        assertThat(mappedValues.containsKey("patient_city")).isTrue()
        assertThat(mappedValues["patient_city"]?.get(0)).isEqualTo("South Rodneychester")
    }

    @Test
    fun `test reading HL7 message from file`() {
        val inputFile = "$hl7TestFileDir/single_message.hl7"
        val message = File(inputFile).readText()
        val mappedMessage = serializer.convertMessageToMap(message, covid19Schema)
        val mappedValues = mappedMessage.row
        mappedValues.forEach {
            println("${it.key}: ${it.value.joinToString()}")
        }
        assertThat(mappedValues.containsKey("patient_city")).isTrue()
        assertThat(mappedValues["patient_city"]?.get(0)).isEqualTo("South Rodneychester")
    }

    @Test
    fun `test reading HL7 batch message from file`() {
        val inputFile = "$hl7TestFileDir/batch_message.hl7"
        val message = File(inputFile).readText()
        val mappedMessage = serializer.convertBatchMessagesToMap(message, covid19Schema)
        val mappedValues = mappedMessage.mappedRows
        println("\ntest reading HL7 batch message from file:\n")
        mappedValues.forEach {
            println("${it.key}: ${it.value.joinToString()}")
        }
        assertThat(mappedValues.containsKey("patient_city")).isTrue()
        val cities = mappedValues["patient_city"]?.toSet()
        assertThat(cities).isEqualTo(setOf("North Taylor", "South Rodneychester"))
        println("Errors:")
        mappedMessage.errors.forEach {
            println(it)
        }
        println("Warnings:")
        mappedMessage.warnings.forEach {
            println(it)
        }
    }

    @Test
    fun `test reading HL7 batch and creating report instance`() {
        val inputFile = "$hl7TestFileDir/batch_message.hl7"
        val message = File(inputFile)
        val source = FileSource(inputFile)
        val readResult = serializer.readExternal(hl7SchemaName, message.inputStream(), source)
        val report = readResult.report ?: fail("Report was null and should not be")
        assertThat(report.getString(0, "patient_city")).isEqualTo("South Rodneychester")
        assertThat(report.getString(1, "patient_city")).isEqualTo("North Taylor")
        assertThat(report.itemCount == 2).isTrue()
        val hospitalized = (0 until report.itemCount).map { report.getString(it, "hospitalized") }
        assertThat(hospitalized.toSet()).isEqualTo(setOf(""))
    }

    @Test
    fun `test XTN phone decoding`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        val mockSegment = mockk<Segment>()
        val emptyPhoneField = mockk<XTN>()
        val emailField = mockk<XTN>()
        val phoneField = mockk<XTN>()
        val deprecatedPhoneField = mockk<XTN>()
        val element = Element("phone", Element.Type.TELEPHONE, hl7Field = "PID-13")

        // Bad field value
        every { mockTerser.getSegment(any()) } returns null
        var phoneNumber = serializer.decodeHl7TelecomData(
            mockTerser, Element("phone", Element.Type.TELEPHONE),
            "PID-BLAH"
        )
        assertThat(phoneNumber).isEqualTo("")

        // Segment not found
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("")

        // No phone number due to zero repetitions
        every { mockTerser.getSegment(any()) } returns mockSegment
        every { mockSegment.getField(any()) } returns emptyArray()
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("")

        // No phone number
        every { mockSegment.getField(any()) } returns arrayOf(emptyPhoneField) // This is only to get the number of reps
        every { mockTerser.get(any()) } returns ""
        every { emptyPhoneField.areaCityCode.isEmpty } returns true
        every { emptyPhoneField.localNumber.isEmpty } returns true
        every { emptyPhoneField.telephoneNumber.isEmpty } returns true
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("")

        // Multiple repetitions with no phone number
        every { mockSegment.getField(any()) } returns arrayOf(emptyPhoneField, emptyPhoneField, emptyPhoneField)
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("")

        // Phone number in deprecated component
        every { deprecatedPhoneField.areaCityCode.isEmpty } returns true
        every { deprecatedPhoneField.localNumber.isEmpty } returns true
        every { deprecatedPhoneField.telephoneNumber.isEmpty } returns false
        every { deprecatedPhoneField.telephoneNumber.valueOrEmpty } returns "(555)555-5555"
        every { mockSegment.getField(any()) } returns arrayOf(deprecatedPhoneField)
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("5555555555:1:")

        // Phone number in newer components.  Will ignore phone number in deprecated component
        every { mockSegment.getField(any()) } returns arrayOf(phoneField)
        every { phoneField.areaCityCode.isEmpty } returns false
        every { phoneField.localNumber.isEmpty } returns false
        every { phoneField.telephoneNumber.value } returns "(555)555-5555"
        every { phoneField.telecommunicationEquipmentType.isEmpty } returns false
        every { phoneField.telecommunicationEquipmentType.valueOrEmpty } returns "PH"
        every { phoneField.countryCode.value } returns "1"
        every { phoneField.areaCityCode.value } returns "666"
        every { phoneField.localNumber.value } returns "7777777"
        every { phoneField.extension.value } returns "9999"
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("6667777777:1:9999")

        // No type assumed to be a phone number
        every { phoneField.telecommunicationEquipmentType.isEmpty } returns true
        every { phoneField.telecommunicationEquipmentType.valueOrEmpty } returns null
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("6667777777:1:9999")

        // A Fax number is not used
        every { phoneField.telecommunicationEquipmentType.isEmpty } returns false
        every { phoneField.telecommunicationEquipmentType.valueOrEmpty } returns "FX"
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("")

        // Test repetitions.  The first repetition for the XTN type can be empty when there is no primary phone number
        every { phoneField.telecommunicationEquipmentType.valueOrEmpty } returns "PH"
        every { emailField.areaCityCode.isEmpty } returns true
        every { emailField.localNumber.isEmpty } returns true
        every { emailField.telephoneNumber.isEmpty } returns true
        every { emailField.emailAddress.valueOrEmpty } returns "dummyemail@cdc.local"
        every { emailField.telecommunicationEquipmentType.isEmpty } returns false
        every { emailField.telecommunicationEquipmentType.valueOrEmpty } returns "Internet"
        every { emailField.telecommunicationUseCode.valueOrEmpty } returns "NET"
        every { mockSegment.getField(any()) } returns arrayOf(emptyPhoneField, emailField, phoneField)
        phoneNumber = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(phoneNumber).isEqualTo("6667777777:1:9999")
    }

    @Test
    fun `test XTN email decoding`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        val mockSegment = mockk<Segment>()
        val emailField = mockk<XTN>()
        val phoneField = mockk<XTN>()
        val element = Element("email", Element.Type.EMAIL, hl7Field = "PID-13")

        // Bad field value
        every { mockTerser.getSegment(any()) } returns null
        var email = serializer.decodeHl7TelecomData(
            mockTerser, Element("email", Element.Type.EMAIL),
            "PID-BLAH"
        )
        assertThat(email).isEqualTo("")

        // Segment not found
        email = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(email).isEqualTo("")

        // No email number due to zero repetitions
        every { mockTerser.getSegment(any()) } returns mockSegment
        every { mockSegment.getField(any()) } returns emptyArray()
        email = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(email).isEqualTo("")

        // No email
        every { mockSegment.getField(any()) } returns arrayOf(phoneField)
        every { phoneField.telecommunicationEquipmentType.isEmpty } returns false
        every { phoneField.telecommunicationEquipmentType.valueOrEmpty } returns "PH"
        email = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(email).isEqualTo("")

        // Test repetitions.
        every { emailField.emailAddress.valueOrEmpty } returns "dummyemail@cdc.local"
        every { emailField.telecommunicationEquipmentType.isEmpty } returns false
        every { emailField.telecommunicationEquipmentType.valueOrEmpty } returns "Internet"
        every { mockSegment.getField(any()) } returns arrayOf(emailField, phoneField)
        email = serializer.decodeHl7TelecomData(mockTerser, element, element.hl7Field!!)
        assertThat(email).isEqualTo("dummyemail@cdc.local")
    }

    @Test
    fun `test date time decoding`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        val mockSegment = mockk<Segment>()
        val mockTS = mockk<TS>()
        val mockDR = mockk<DR>()
        val mockDTM = mockk<DTM>()
        val now = OffsetDateTime.now()
        val nowAsDate = Date.from(now.toInstant())
        val dateTimeElement = Element("field", hl7Field = "OBX-14", type = Element.Type.DATETIME)
        val warnings = mutableListOf<String>()
        val dateFormatterWithTimeZone = DateTimeFormatter.ofPattern(Element.datetimePattern)
        val dateFormatterNoTimeZone = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

        // Segment not found
        every { mockTerser.getSegment(any()) } returns null
        var dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo("")

        // Bad field value
        every { mockTerser.getSegment(any()) } returns mockSegment
        dateTime = serializer.decodeHl7DateTime(
            mockTerser, Element("field", hl7Field = "OBX-Blah"),
            "OBX-Blah", warnings
        )
        assertThat(dateTime).isEqualTo("")

        // No field value
        every { mockSegment.getField(any(), any()) } returns null
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo("")

        // Field value is TS, but no time
        every { mockSegment.getField(any(), any()) } returns mockTS
        every { mockTS.time } returns null
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo("")

        // Field value is TS has a time
        every { mockTS.time } returns mockDTM
        every { mockTS.time.valueAsDate } returns nowAsDate
        every { mockTS.time.value } returns dateFormatterWithTimeZone.format(now)
        every { mockTS.time.gmtOffset } returns 0
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo(dateFormatterWithTimeZone.format(now.withOffsetSameInstant(ZoneOffset.UTC)))

        // Field value is TS has a time, but no GMT offset
        every { mockTS.time } returns mockDTM
        val cal = Calendar.getInstance()
        cal.time = nowAsDate
        every { mockTS.time.valueAsCalendar } returns cal
        every { mockTS.time.value } returns dateFormatterWithTimeZone.format(now)
        every { mockTS.time.gmtOffset } returns -99
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo(dateFormatterWithTimeZone.format(now.withOffsetSameInstant(ZoneOffset.UTC)))

        // Field value is DR, but no range
        every { mockSegment.getField(any(), any()) } returns mockDR
        every { mockDR.rangeStartDateTime } returns null
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo("")

        // Field value is DR has a range, but with no time
        every { mockDR.rangeStartDateTime } returns mockTS
        every { mockDR.rangeStartDateTime.time } returns null
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo("")

        // Field value is DR and has a time
        every { mockDR.rangeStartDateTime } returns mockTS
        every { mockDR.rangeStartDateTime.time } returns mockDTM
        every { mockDR.rangeStartDateTime.time.valueAsDate } returns nowAsDate
        every { mockDR.rangeStartDateTime.time.gmtOffset } returns -99
        every { mockDR.rangeStartDateTime.time.valueAsCalendar } returns cal
        dateTime = serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo(dateFormatterWithTimeZone.format(now.withOffsetSameInstant(ZoneOffset.UTC)))

        // Generate a warning for not having the timezone offsets
        every { mockDR.rangeStartDateTime } returns mockTS
        every { mockDR.rangeStartDateTime.time } returns mockDTM
        every { mockDR.rangeStartDateTime.time.valueAsDate } returns nowAsDate
        every { mockDR.rangeStartDateTime.time.value } returns dateFormatterNoTimeZone.format(now)
        warnings.clear()
        serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
        assertThat(warnings.size == 1).isTrue()

        // Test a bit more the regex for the warning
        fun testForTimestampWarning(dateString: String, numExpectedWarnings: Int) {
            // Note the data type here does not affect how the raw string gets check for a warning
            every { mockDR.rangeStartDateTime.time.valueAsDate.toInstant() } returns Date().toInstant()
            every { mockDR.toString() } returns dateString
            every { mockSegment.getField(any(), any()) } returns mockDR
            warnings.clear()
            serializer.decodeHl7DateTime(mockTerser, dateTimeElement, dateTimeElement.hl7Field!!, warnings)
            assertThat(warnings.size).isEqualTo(numExpectedWarnings)
        }

        testForTimestampWarning("TS[202101011200]", 1)
        testForTimestampWarning("TS[202101011200.0000]", 1)
        testForTimestampWarning("TS[2021010112-0400]", 1)
        testForTimestampWarning("DR[202101011200.0000-4000]", 0)
        testForTimestampWarning("TS[202101011200.0000+4000]", 0)
        testForTimestampWarning("DR[202101011259+4000]", 0)
    }

    @Test
    fun `test date decoding`() {
        val mockTerser = mockk<Terser>()
        val mockSegment = mockk<Segment>()
        val mockDT = mockk<DT>()
        val dateElement = Element("field", hl7Field = "OBX-14", type = Element.Type.DATE)
        val dateFormatterDate = DateTimeFormatter.ofPattern(Element.datePattern)
        val warnings = mutableListOf<String>()

        every { mockTerser.getSegment(any()) } returns mockSegment

        // Field value is DT with a date
        val date = LocalDate.of(1995, 1, 1)
        val formattedDate = dateFormatterDate.format(date)
        every { mockDT.toString() } returns formattedDate
        every { mockDT.year } returns date.year
        every { mockDT.month } returns date.monthValue
        every { mockDT.day } returns date.dayOfMonth
        every { mockSegment.getField(any(), any()) } returns mockDT
        val dateTime = serializer.decodeHl7DateTime(mockTerser, dateElement, dateElement.hl7Field!!, warnings)
        assertThat(dateTime).isEqualTo(formattedDate)

        // Test a bit more the regex for the warning
        fun testForDateWarning(dateString: String, numExpectedWarnings: Int) {
            every { mockDT.toString() } returns dateString
            every { mockDT.year } returns 1995
            every { mockDT.month } returns 1
            every { mockDT.day } returns 1
            every { mockSegment.getField(any(), any()) } returns mockDT
            warnings.clear()
            serializer.decodeHl7DateTime(mockTerser, dateElement, dateElement.hl7Field!!, warnings)
            assertThat(warnings.size).isEqualTo(numExpectedWarnings)
        }

        testForDateWarning("DT[19950101]", 0)
        testForDateWarning("TS[19950101120101.0001+4000]", 0)
        testForDateWarning("DT[199501]", 1)
        testForDateWarning("DT[1995]", 1)
    }

    @Test
    fun `test reading message with international characters from serializer`() {
        // Sample UTF-8 taken from https://www.kermitproject.org/utf8.html as a byte array, so we are not
        // restricted by the encoding of this code file
        val greekString = String(
            byteArrayOf(-50, -100, -49, -128, -50, -65, -49, -127, -49, -114),
            Charsets.UTF_8
        )

        // Java strings are stored as UTF-16
        val intMessage = """MSH|^~\&|CDC PRIME - Atlanta, Georgia (Dekalb)^2.16.840.1.114222.4.1.237821^ISO|Avante at Ormond Beach^10D0876999^CLIA|||20210210170737||ORU^R01^ORU_R01|371784|P|2.5.1|||NE|NE|USA||||PHLabReportNoAck^ELR_Receiver^2.16.840.1.113883.9.11^ISO
SFT|Centers for Disease Control and Prevention|0.1-SNAPSHOT|PRIME ReportStream|0.1-SNAPSHOT||20210210
PID|1||2a14112c-ece1-4f82-915c-7b3a8d152eda^^^Avante at Ormond Beach^PI||$greekString^Kareem^Millie^^^^L||19580810|F||2106-3^White^HL70005^^^^2.5.1|688 Leighann Inlet^^South Rodneychester^TX^67071||^PRN^^roscoe.wilkinson@email.com^1^211^2240784|||||||||U^Unknown^HL70189||||||||N
ORC|RE|73a6e9bd-aaec-418e-813a-0ad33366ca85|73a6e9bd-aaec-418e-813a-0ad33366ca85|||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI||^WPN^^^1^386^6825220|20210209||||||Avante at Ormond Beach|170 North King Road^^Ormond Beach^FL^32174^^^^12127|^WPN^^jbrush@avantecenters.com^1^407^7397506|^^^^32174
OBR|1|73a6e9bd-aaec-418e-813a-0ad33366ca85||94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN|||202102090000-0600|202102090000-0600||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI|^WPN^^^1^386^6825220|||||202102090000-0600|||F
OBX|1|CWE|94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN||260415000^Not detected^SCT|||N^Normal (applies to non-numeric results)^HL70078|||F|||202102090000-0600|||CareStart COVID-19 Antigen test_Access Bio, Inc._EUA^^99ELR||202102090000-0600||||Avante at Ormond Beach^^^^^CLIA&2.16.840.1.113883.4.7&ISO^^^^10D0876999^CLIA|170 North King Road^^Ormond Beach^FL^32174^^^^12127
NTE|1|L|This is a comment|RE
OBX|2|CWE|95418-0^Whether patient is employed in a healthcare setting^LN^^^^2.69||Y^Yes^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|3|CWE|95417-2^First test for condition of interest^LN^^^^2.69||Y^Yes^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|4|CWE|95421-4^Resides in a congregate care setting^LN^^^^2.69||N^No^HL70136||||||F|||202102090000-0600|||||||||||||||QST
OBX|5|CWE|95419-8^Has symptoms related to condition of interest^LN^^^^2.69||N^No^HL70136||||||F|||202102090000-0600|||||||||||||||QST
SPM|1|||258500001^Nasopharyngeal swab^SCT||||71836000^Nasopharyngeal structure (body structure)^SCT^^^^2020-09-01|||||||||202102090000-0600^202102090000-0600
NTE|1|L|This is a final comment|RE"""

        // arrange
        val mcf = CanonicalModelClassFactory("2.5.1")
        context.modelClassFactory = mcf
        val parser = context.pipeParser
        // act
        val reg = "[\r\n]".toRegex()
        val cleanedMessage = reg.replace(intMessage, "\r")
        val hapiMsg = parser.parse(cleanedMessage)
        val terser = Terser(hapiMsg)
        // assert
        assertThat(terser.get("/.PID-5-1")).isEqualTo(greekString)
    }

    @Test
    fun `test terser spec generator`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        assertThat(serializer.getTerserSpec("MSH-1-1")).isEqualTo("/MSH-1-1")
        assertThat(serializer.getTerserSpec("PID-1")).isEqualTo("/.PID-1")
        assertThat(serializer.getTerserSpec("")).isEqualTo("/.")
    }

    @Test
    fun `test setTelephoneComponents for patient`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit
        every { mockTerser.get("/PATIENT_RESULT/PATIENT/PID-13(0)-2") } returns ""

        val patientPathSpec = serializer.formPathSpec("PID-13")
        val patientElement = Element("patient_phone_number", hl7Field = "PID-13", type = Element.Type.TELEPHONE)
        serializer.setTelephoneComponent(
            mockTerser,
            "5555555555:1:",
            patientPathSpec,
            patientElement,
            Hl7Configuration.PhoneNumberFormatting.ONLY_DIGITS_IN_COMPONENT_ONE
        )

        verify {
            mockTerser.set("/PATIENT_RESULT/PATIENT/PID-13(0)-1", "5555555555")
            mockTerser.set("/PATIENT_RESULT/PATIENT/PID-13(0)-2", "PRN")
            mockTerser.set("/PATIENT_RESULT/PATIENT/PID-13(0)-3", "PH")
            mockTerser.set("/PATIENT_RESULT/PATIENT/PID-13(0)-5", "1")
            mockTerser.set("/PATIENT_RESULT/PATIENT/PID-13(0)-6", "555")
            mockTerser.set("/PATIENT_RESULT/PATIENT/PID-13(0)-7", "5555555")
        }
    }

    @Test
    fun `test setTelephoneComponents for facility`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit

        val facilityPathSpec = serializer.formPathSpec("ORC-23")
        val facilityElement = Element(
            "ordering_facility_phone_number",
            hl7Field = "ORC-23",
            type = Element.Type.TELEPHONE
        )
        serializer.setTelephoneComponent(
            mockTerser,
            "5555555555:1:3333",
            facilityPathSpec,
            facilityElement,
            Hl7Configuration.PhoneNumberFormatting.STANDARD
        )

        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-1", "(555)555-5555X3333")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-2", "WPN")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-3", "PH")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-5", "1")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-6", "555")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-7", "5555555")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-23-8", "3333")
        }
    }

    @Test
    fun `test setCliaComponents`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit

        serializer.setCliaComponent(
            mockTerser,
            "XYZ",
            "OBX-23-10"
        )

        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/OBSERVATION/OBX-23-10", "XYZ")
        }
    }

    @Test
    fun `test setCliaComponents in HD`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit
        val hl7Field = "ORC-3-3"
        val value = "dummy"

        serializer.setCliaComponent(
            mockTerser,
            value,
            hl7Field
        )

        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-3-3", value)
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-3-4", "CLIA")
        }
    }

    @Test
    fun `test getSchoolId`() {
        // Get a bunch of k12 rows
        val testCSV = File("./src/test/unit_test_files/pdi-covid-19-wa-k12.csv").inputStream()
        val testReport = csvSerializer
            .readExternal("primedatainput/pdi-covid-19", testCSV, TestSource)
            .report ?: fail()

        // This row is the happy path
        val rawValidFacilityName = testReport.getString(0, "ordering_facility_name") ?: fail()
        val validNCES = serializer.getSchoolId(testReport, 0, rawValidFacilityName)
        assertThat(validNCES).isEqualTo("530825001381")

        // This row doesn't match on zip code
        val rawInvalidZip = testReport.getString(8, "ordering_facility_name") ?: fail()
        val invalidZip = serializer.getSchoolId(testReport, 8, rawInvalidZip)
        assertThat(invalidZip).isNull()

        // This row does a best match
        val rawPartialName = testReport.getString(10, "ordering_facility_name") ?: fail()
        val partialNCES = serializer.getSchoolId(testReport, 10, rawPartialName)
        assertThat(partialNCES).isEqualTo("530825001381")

        // This row doesn't match on site type
        val rawInvalidSite = testReport.getString(11, "ordering_facility_name") ?: fail()
        val invalidSite = serializer.getSchoolId(testReport, 11, rawInvalidSite)
        assertThat(invalidSite).isNull()

        // There are three schools that have the same first name in this zip-code
        val rawHighSchool = testReport.getString(12, "ordering_facility_name") ?: fail()
        val highSchool = serializer.getSchoolId(testReport, 12, rawHighSchool)
        assertThat(highSchool).isEqualTo("530042000099")

        // There are three schools that have the same first name in this zip-code. This one has a very long name.
        val rawPartnershipSchool = testReport.getString(13, "ordering_facility_name") ?: fail()
        val partnershipSchool = serializer.getSchoolId(testReport, 13, rawPartnershipSchool)
        assertThat(partnershipSchool).isEqualTo("530042003476")
    }

    @Test
    fun `test setOrderingFacilityComponent`() {
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit
        val facilityName = "Very Long Facility Name That Should Truncate After Here"
        // Get a bunch of k12 rows
        val testCSV = File("./src/test/unit_test_files/pdi-covid-19-wa-k12.csv").inputStream()
        val testReport = csvSerializer
            .readExternal("primedatainput/pdi-covid-19", testCSV, TestSource)
            .report ?: fail()

        // Test with STANDARD
        serializer.setOrderingFacilityComponent(
            mockTerser,
            facilityName,
            useOrderingFacilityName = Hl7Configuration.OrderingFacilityName.STANDARD,
            testReport,
            row = 0
        )

        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-1", facilityName.take(50))
        }
    }

    @Test
    fun `test setOrderingFacilityComponent with Organization Name`() {
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit
        val facilityName = "Very Long Facility Name That Should Truncate After Here"
        // Get a bunch of k12 rows
        val testCSV = File("./src/test/unit_test_files/pdi-covid-19-wa-k12.csv").inputStream()
        val testReport = csvSerializer
            .readExternal("primedatainput/pdi-covid-19", testCSV, TestSource)
            .report ?: fail()

        // Test with STANDARD
        serializer.setOrderingFacilityComponent(
            mockTerser,
            facilityName,
            useOrderingFacilityName = Hl7Configuration.OrderingFacilityName.ORGANIZATION_NAME,
            testReport,
            row = 0
        )

        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-1", "Spokane School District")
        }
    }

    @Test
    fun `test setPlainOrderingFacility`() {
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit
        val facilityName = "Very Long Facility Name That Should Truncate After Here"
        serializer.setPlainOrderingFacility(mockTerser, facilityName)
        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-1", facilityName.take(50))
        }
    }

    @Test
    fun `test setNCESOrderingFacility`() {
        val mockTerser = mockk<Terser>()
        every { mockTerser.set(any(), any()) } returns Unit
        val facilityName = "Very Long Facility Name That Should Truncate After Here"
        val ncesId = "A00000009"
        serializer.setNCESOrderingFacility(mockTerser, facilityName, ncesId)
        verify {
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-1", "${facilityName.take(32)}_NCES_$ncesId")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-6-1", "NCES.IES")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-6-2", "2.16.840.1.113883.3.8589.4.1.119")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-6-3", "ISO")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-7", "XX")
            mockTerser.set("/PATIENT_RESULT/ORDER_OBSERVATION/ORC-21-10", ncesId)
        }
    }

    @Test
    fun `test canonicalSchoolName`() {
        // Use NCES actual table values to test
        val senior = serializer.canonicalizeSchoolName("SHREWSBURY SR HIGH")
        assertThat(senior).isEqualTo("SHREWSBURY SENIOR HIGH")

        val stJohns = serializer.canonicalizeSchoolName("ST JOHN'S HIGH SCHOOL")
        assertThat(stJohns).isEqualTo("ST JOHNS HIGH")

        val sizer = serializer.canonicalizeSchoolName("SIZER SCHOOL: A NORTH CENTRAL CHARTER ESSENTIAL SCHOOL")
        assertThat(sizer).isEqualTo("SIZER NORTH CENTRAL CHARTER ESSENTIAL")

        val elem = serializer.canonicalizeSchoolName("NORTHERN LINCOLN ELEM.")
        assertThat(elem).isEqualTo("NORTHERN LINCOLN ELEMENTARY")

        val elem2 = serializer.canonicalizeSchoolName("WAKEFIELD HILLS EL. SCHOOL")
        assertThat(elem2).isEqualTo("WAKEFIELD HILLS ELEMENTARY")

        val jr1 = serializer.canonicalizeSchoolName("M. L. KING JR. MIDDLE SCHOOL")
        assertThat(jr1).isEqualTo("KING JR MIDDLE")

        val jr2 = serializer.canonicalizeSchoolName("CHURCHILL JR HIGH SCHOOL")
        assertThat(jr2).isEqualTo("CHURCHILL JUNIOR HIGH")

        val tahono = serializer.canonicalizeSchoolName("TOHONO O`ODHAM HIGH SCHOOL")
        assertThat(tahono).isEqualTo("TOHONO ODHAM HIGH")

        val possesive = serializer.canonicalizeSchoolName("ST TIMOTHY'S EPISCOPAL DAY SCHOOL")
        assertThat(possesive).isEqualTo("ST TIMOTHYS EPISCOPAL DAY")

        val tse = serializer.canonicalizeSchoolName("TSE'II'AHI' COMMUNITY SCHOOL")
        assertThat(tse).isEqualTo("TSE II AHI COMMUNITY")
    }

    @Test
    fun `test setTruncationLimitWithEncoding`() {

        val testValueWithSpecialChars = "Test & Value ~ Text ^ String"
        val testValueNoSpecialChars = "Test Value Text String"
        val testLimit = 20
        val newLimitWithSpecialChars = serializer.getTruncationLimitWithEncoding(testValueWithSpecialChars, testLimit)
        val newLimitNoSpecialChars = serializer.getTruncationLimitWithEncoding(testValueNoSpecialChars, testLimit)

        assertEquals(newLimitWithSpecialChars, 16)
        assertEquals(newLimitNoSpecialChars, testLimit)
    }

    @Test
    fun `test incorrect HL7 content`() {
        val settings = FileSettings("./settings")
        val serializer = Hl7Serializer(metadata, settings)

        val emptyHL7 = ByteArrayInputStream("".toByteArray())
        var result = serializer.readExternal(hl7SchemaName, emptyHL7, TestSource)
        assertThat(result.report).isNotNull()
        assertThat(result.report!!.itemCount).isEqualTo(0)

        val csvContent = ByteArrayInputStream("a,b,c\n1,2,3".toByteArray())
        result = serializer.readExternal(hl7SchemaName, csvContent, TestSource)
        assertThat(result.errors).isNotEmpty()
        assertThat(result.report).isNull()

        val incompleteHL7 = ByteArrayInputStream("MSH|^~\\&|CD".toByteArray())
        result = serializer.readExternal(hl7SchemaName, incompleteHL7, TestSource)
        assertThat(result.errors).isNotEmpty()
        assertThat(result.report).isNull()

        // This data will throw a EncodingNotSupportedException in the serializer when parsing the message
        val incompleteHL7v2 = ByteArrayInputStream(
            """
            MSH|^~\&|CDC PRIME - Atlanta, Georgia (Dekalb)^2.16.840.1.114222.4.1.237821^ISO|Avante at Ormond Beach^10D0876999^CLIA|||20210210170737||ORU^R01^ORU_R01|371784|P|2.5.1|||NE|NE|USA||||PHLabReportNoAck^ELR_Receiver^2.16.840.1.113883.9.11^ISO
            SFT|Centers for Disease Control and Prevention|0.1-SNAPSHOT|PRIME ReportStream|0.1-SNAPSHOT||20210210
            PID|1||2a14112c-ece1-4f82-915c-7b3a8d152eda^^^Avante at Ormond Beach^PI||Doe^Kareem^Millie^^^^L||19580810|F||2106-3^White^HL70005^^^^2.5.1|688 Leighann Inlet^^South Rodneychester^TX^67071||^PRN^^roscoe.wilkinson@email.com^1^211^2240784|||||||||U^Unknown^HL70189||||||||N
            """.trimIndent().toByteArray()
        )
        result = serializer.readExternal(hl7SchemaName, incompleteHL7v2, TestSource)
        assertThat(result.errors).isNotEmpty()
        assertThat(result.report).isNull()

        // This data will throw a HL7Exception in the serializer when parsing the message
        val wrongHL7Version = ByteArrayInputStream(
            """
            MSH|^~\&|CDC PRIME - Atlanta, Georgia (Dekalb)^2.16.840.1.114222.4.1.237821^ISO|Avante at Ormond Beach^10D0876999^CLIA|||20210210170737||ORU^R01^ORU_R01|371784|P|5.0.0|||NE|NE|USA||||PHLabReportNoAck^ELR_Receiver^2.16.840.1.113883.9.11^ISO
            SFT|Centers for Disease Control and Prevention|0.1-SNAPSHOT|PRIME ReportStream|0.1-SNAPSHOT||20210210
            PID|1||2a14112c-ece1-4f82-915c-7b3a8d152eda^^^Avante at Ormond Beach^PI||Doe^Kareem^Millie^^^^L||19580810|F||2106-3^White^HL70005^^^^2.5.1|688 Leighann Inlet^^South Rodneychester^TX^67071||^PRN^^roscoe.wilkinson@email.com^1^211^2240784|||||||||U^Unknown^HL70189||||||||N
            ORC|RE|73a6e9bd-aaec-418e-813a-0ad33366ca85|73a6e9bd-aaec-418e-813a-0ad33366ca85|||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI||^WPN^^^1^386^6825220|20210209||||||Avante at Ormond Beach|170 North King Road^^Ormond Beach^FL^32174^^^^12127|^WPN^^jbrush@avantecenters.com^1^407^7397506|^^^^32174
            OBR|1|73a6e9bd-aaec-418e-813a-0ad33366ca85||94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN|||202102090000-0600|202102090000-0600||||||||1629082607^Eddin^Husam^^^^^^CMS&2.16.840.1.113883.3.249&ISO^^^^NPI|^WPN^^^1^386^6825220|||||202102090000-0600|||F
            OBX|1|CWE|94558-4^SARS-CoV-2 (COVID-19) Ag [Presence] in Respiratory specimen by Rapid immunoassay^LN||260415000^Not detected^SCT|||N^Normal (applies to non-numeric results)^HL70078|||F|||202102090000-0600|||CareStart COVID-19 Antigen test_Access Bio, Inc._EUA^^99ELR||202102090000-0600||||Avante at Ormond Beach^^^^^CLIA&2.16.840.1.113883.4.7&ISO^^^^10D0876999^CLIA|170 North King Road^^Ormond Beach^FL^32174^^^^12127
            """.trimIndent().toByteArray()
        )
        result = serializer.readExternal(hl7SchemaName, wrongHL7Version, TestSource)
        assertThat(result.errors).isNotEmpty()
        assertThat(result.report).isNull()
    }

    @Test
    fun `test cliaForSender`() {

        // arrange
        val mcf = CanonicalModelClassFactory("2.5.1")
        context.modelClassFactory = mcf
        val parser = context.pipeParser
        // act
        val reg = "[\r\n]".toRegex()

        // SenderID is set to "fake" in this CSV
        val csvContent = ByteArrayInputStream("senderId,testOrdered,testName,testCodingSystem,testResult,testResultText,testPerformed,testResultCodingSystem,testResultDate,testReportDate,testOrderedDate,specimenCollectedDate,deviceIdentifier,deviceName,specimenId,serialNumber,patientAge,patientAgeUnits,patientDob,patientRace,patientRaceText,patientEthnicity,patientEthnicityText,patientSex,patientZip,patientCounty,orderingProviderNpi,orderingProviderLname,orderingProviderFname,orderingProviderZip,performingFacility,performingFacilityName,performingFacilityStreet,performingFacilityStreet2,performingFacilityCity,performingFacilityState,performingFacilityZip,performingFacilityCounty,performingFacilityPhone,orderingFacilityName,orderingFacilityStreet,orderingFacilityStreet2,orderingFacilityCity,orderingFacilityState,orderingFacilityZip,orderingFacilityCounty,orderingFacilityPhone,specimenSource,patientNameLast,patientNameFirst,patientNameMiddle,patientUniqueId,patientHomeAddress,patientHomeAddress2,patientCity,patientState,patientPhone,patientPhoneArea,orderingProviderAddress,orderingProviderAddress2,orderingProviderCity,orderingProviderState,orderingProviderPhone,orderingProviderPhoneArea,firstTest,previousTestType,previousTestDate,previousTestResult,correctedTestId,healthcareEmployee,healthcareEmployeeType,symptomatic,symptomsList,hospitalized,hospitalizedCode,symptomsIcu,congregateResident,congregateResidentType,pregnant,pregnantText,patientEmail,reportingFacility\nfake,94531-1,SARS coronavirus 2 RNA panel - Respiratory specimen by NAA with probe detection,LN,260415000,Not Detected,94558-4,SCT,202110062022-0400,202110062022-0400,20211007,20211007,00382902560821,BD Veritor System for Rapid Detection of SARS-CoV-2*,4efd9df8-9424-4e50-b168-f3aa894bfa42,4efd9df8-9424-4e50-b168-f3aa894bfa42,45,yr,1975-10-10,2106-3,White,2135-2,Hispanic or Latino,M,93307,Kern County,1760085880,,,93312,05D2191150,Inovia Pharmacy,9902 Brimhall rd ste 100,,Bakersfield,CA,93312,Kern County,+16618297861,Inovia Pharmacy,9902 Brimhall rd ste 100,,Bakersfield,CA,93312,Kern County,+16618297861,445297001,Tapia,Jose,,e553c462-6bad-4e42-ab1e-0879b797aa31,1211 Dawn st,,Bakersfield,CA,+16614933107,661,9902 BRIMHALL RD STE 100,,BAKERSFIELD,CA,+16618297861,661,UNK,,,,,,,UNK,,NO,,NO,NO,,261665006,UNK,,1760085880".toByteArray()) // ktlint-disable max-line-length
        val schema = "direct/direct-covid-19"

        val hl7Config = mockkClass(Hl7Configuration::class).also {
            every { it.replaceValue }.returns(mapOf())
            every { it.format }.returns(Report.Format.HL7)
            every { it.useTestProcessingMode }.returns(false)
            every { it.suppressQstForAoe }.returns(false)
            every { it.suppressAoe }.returns(false)
            every { it.suppressHl7Fields }.returns(null)
            every { it.useBlankInsteadOfUnknown }.returns(null)
            every { it.convertTimestampToDateTime }.returns(null)
            every { it.truncateHDNamespaceIds }.returns(false)
            every { it.phoneNumberFormatting }.returns(Hl7Configuration.PhoneNumberFormatting.STANDARD)
            every { it.usePid14ForPatientEmail }.returns(false)
            every { it.reportingFacilityName }.returns(null)
            every { it.reportingFacilityId }.returns(null)
            every { it.reportingFacilityIdType }.returns(null)
            every { it.cliaForOutOfStateTesting }.returns(null)
            every { it.useOrderingFacilityName }.returns(Hl7Configuration.OrderingFacilityName.STANDARD)
            every { it.cliaForSender }.returns(mapOf("fake1" to "ABCTEXT123", "fake" to "10D1234567"))
            every { it.defaultAoeToUnknown }.returns(false)
        }
        val receiver = mockkClass(Receiver::class).also {
            every { it.translation }.returns(hl7Config)
            every { it.format }.returns(Report.Format.HL7)
            every { it.organizationName }.returns("ca-dph")
        }

        val testReport = csvSerializer.readExternal(schema, csvContent, listOf(TestSource), receiver).report ?: fail()
        val output = serializer.createMessage(testReport, 0)

        val cleanedMessage = reg.replace(output, "\r")
        val hapiMsg = parser.parse(cleanedMessage)
        val terser = Terser(hapiMsg)
        val cliaTersed = terser.get("/MSH-4-2")

        assertThat(cliaTersed).isEqualTo("10D1234567")

        // Test when sender is not found or blank
        val csvContentSenderNotFound = ByteArrayInputStream("senderId,testOrdered,testName,testCodingSystem,testResult,testResultText,testPerformed,testResultCodingSystem,testResultDate,testReportDate,testOrderedDate,specimenCollectedDate,deviceIdentifier,deviceName,specimenId,serialNumber,patientAge,patientAgeUnits,patientDob,patientRace,patientRaceText,patientEthnicity,patientEthnicityText,patientSex,patientZip,patientCounty,orderingProviderNpi,orderingProviderLname,orderingProviderFname,orderingProviderZip,performingFacility,performingFacilityName,performingFacilityStreet,performingFacilityStreet2,performingFacilityCity,performingFacilityState,performingFacilityZip,performingFacilityCounty,performingFacilityPhone,orderingFacilityName,orderingFacilityStreet,orderingFacilityStreet2,orderingFacilityCity,orderingFacilityState,orderingFacilityZip,orderingFacilityCounty,orderingFacilityPhone,specimenSource,patientNameLast,patientNameFirst,patientNameMiddle,patientUniqueId,patientHomeAddress,patientHomeAddress2,patientCity,patientState,patientPhone,patientPhoneArea,orderingProviderAddress,orderingProviderAddress2,orderingProviderCity,orderingProviderState,orderingProviderPhone,orderingProviderPhoneArea,firstTest,previousTestType,previousTestDate,previousTestResult,correctedTestId,healthcareEmployee,healthcareEmployeeType,symptomatic,symptomsList,hospitalized,hospitalizedCode,symptomsIcu,congregateResident,congregateResidentType,pregnant,pregnantText,patientEmail,reportingFacility\nfake,94531-1,SARS coronavirus 2 RNA panel - Respiratory specimen by NAA with probe detection,LN,260415000,Not Detected,94558-4,SCT,202110062022-0400,202110062022-0400,20211007,20211007,00382902560821,BD Veritor System for Rapid Detection of SARS-CoV-2*,4efd9df8-9424-4e50-b168-f3aa894bfa42,4efd9df8-9424-4e50-b168-f3aa894bfa42,45,yr,1975-10-10,2106-3,White,2135-2,Hispanic or Latino,M,93307,Kern County,1760085880,,,93312,05D2191150,Inovia Pharmacy,9902 Brimhall rd ste 100,,Bakersfield,CA,93312,Kern County,+16618297861,Inovia Pharmacy,9902 Brimhall rd ste 100,,Bakersfield,CA,93312,Kern County,+16618297861,445297001,Tapia,Jose,,e553c462-6bad-4e42-ab1e-0879b797aa31,1211 Dawn st,,Bakersfield,CA,+16614933107,661,9902 BRIMHALL RD STE 100,,BAKERSFIELD,CA,+16618297861,661,UNK,,,,,,,UNK,,NO,,NO,NO,,261665006,UNK,,1760085880".toByteArray()) // ktlint-disable max-line-length

        val hl7ConfigSenderNotFound = mockkClass(Hl7Configuration::class).also {
            every { it.replaceValue }.returns(mapOf())
            every { it.format }.returns(Report.Format.HL7)
            every { it.useTestProcessingMode }.returns(false)
            every { it.suppressQstForAoe }.returns(false)
            every { it.suppressAoe }.returns(false)
            every { it.suppressHl7Fields }.returns(null)
            every { it.useBlankInsteadOfUnknown }.returns(null)
            every { it.convertTimestampToDateTime }.returns(null)
            every { it.truncateHDNamespaceIds }.returns(false)
            every { it.phoneNumberFormatting }.returns(Hl7Configuration.PhoneNumberFormatting.STANDARD)
            every { it.usePid14ForPatientEmail }.returns(false)
            every { it.reportingFacilityName }.returns(null)
            every { it.reportingFacilityId }.returns(null)
            every { it.reportingFacilityIdType }.returns(null)
            every { it.cliaForOutOfStateTesting }.returns(null)
            every { it.useOrderingFacilityName }.returns(Hl7Configuration.OrderingFacilityName.STANDARD)
            every { it.cliaForSender }.returns(mapOf("NotFound" to "ABCTEXT123", "" to "FAKETXT123"))
            every { it.defaultAoeToUnknown }.returns(false)
        }

        val receiverSenderNotFound = mockkClass(Receiver::class).also {
            every { it.translation }.returns(hl7ConfigSenderNotFound)
            every { it.format }.returns(Report.Format.HL7)
            every { it.organizationName }.returns("ca-dph")
        }

        val testRptSenderNotFound = csvSerializer.readExternal(schema, csvContentSenderNotFound, listOf(TestSource), receiverSenderNotFound).report ?: fail() // ktlint-disable max-line-length
        val outputSenderNotFound = serializer.createMessage(testRptSenderNotFound, 0)

        val cleanedMessageSenderNotFound = reg.replace(outputSenderNotFound, "\r")
        val hapiMsgSenderNotFound = parser.parse(cleanedMessageSenderNotFound)
        val terserSenderNotFound = Terser(hapiMsgSenderNotFound)
        val cliaTersedSenderNotFound = terserSenderNotFound.get("/MSH-4-2")

        assertThat(cliaTersedSenderNotFound).isNotEqualTo("ABCTEXT123")
        assertThat(cliaTersedSenderNotFound).isNotEqualTo("FAKETXT123")
    }

    @Test
    fun `test replaceValue`() {

        // arrange
        val mcf = CanonicalModelClassFactory("2.5.1")
        context.modelClassFactory = mcf
        val parser = context.pipeParser
        // act
        val reg = "[\r\n]".toRegex()

        // SenderID is set to "fake" in this CSV
        val csvContent = ByteArrayInputStream("senderId,testOrdered,testName,testCodingSystem,testResult,testResultText,testPerformed,testResultCodingSystem,testResultDate,testReportDate,testOrderedDate,specimenCollectedDate,deviceIdentifier,deviceName,specimenId,serialNumber,patientAge,patientAgeUnits,patientDob,patientRace,patientRaceText,patientEthnicity,patientEthnicityText,patientSex,patientZip,patientCounty,orderingProviderNpi,orderingProviderLname,orderingProviderFname,orderingProviderZip,performingFacility,performingFacilityName,performingFacilityStreet,performingFacilityStreet2,performingFacilityCity,performingFacilityState,performingFacilityZip,performingFacilityCounty,performingFacilityPhone,orderingFacilityName,orderingFacilityStreet,orderingFacilityStreet2,orderingFacilityCity,orderingFacilityState,orderingFacilityZip,orderingFacilityCounty,orderingFacilityPhone,specimenSource,patientNameLast,patientNameFirst,patientNameMiddle,patientUniqueId,patientHomeAddress,patientHomeAddress2,patientCity,patientState,patientPhone,patientPhoneArea,orderingProviderAddress,orderingProviderAddress2,orderingProviderCity,orderingProviderState,orderingProviderPhone,orderingProviderPhoneArea,firstTest,previousTestType,previousTestDate,previousTestResult,correctedTestId,healthcareEmployee,healthcareEmployeeType,symptomatic,symptomsList,hospitalized,hospitalizedCode,symptomsIcu,congregateResident,congregateResidentType,pregnant,pregnantText,patientEmail,reportingFacility\nfake,94531-1,SARS coronavirus 2 RNA panel - Respiratory specimen by NAA with probe detection,LN,260415000,Not Detected,94558-4,SCT,202110062022-0400,202110062022-0400,20211007,20211007,00382902560821,BD Veritor System for Rapid Detection of SARS-CoV-2*,4efd9df8-9424-4e50-b168-f3aa894bfa42,4efd9df8-9424-4e50-b168-f3aa894bfa42,45,yr,1975-10-10,2106-3,White,2135-2,Hispanic or Latino,M,93307,Kern County,1760085880,,,93312,05D2191150,Inovia Pharmacy,9902 Brimhall rd ste 100,,Bakersfield,CA,93312,Kern County,+16618297861,Inovia Pharmacy,9902 Brimhall rd ste 100,,Bakersfield,CA,93312,Kern County,+16618297861,445297001,Tapia,Jose,,e553c462-6bad-4e42-ab1e-0879b797aa31,1211 Dawn st,,Bakersfield,CA,+16614933107,661,9902 BRIMHALL RD STE 100,,BAKERSFIELD,CA,+16618297861,661,UNK,,,,,,,UNK,,NO,,NO,NO,,261665006,UNK,,1760085880".toByteArray()) // ktlint-disable max-line-length
        val schema = "direct/direct-covid-19"

        val hl7Config = mockkClass(Hl7Configuration::class).also {
            every { it.replaceValue }.returns(
                mapOf(
                    "" to "ABCTEXT123",
                    "fake1" to "ABCTEXT123",
                    "MSH-4-1" to "success",
                    "MSH-4-2" to "correctText,-,YES!",
                    "MSH-4-3" to "MSH-4-2",
                    "MSH-10" to "yeah,/,MSH-4-1"
                )
            )
            every { it.format }.returns(Report.Format.HL7)
            every { it.useTestProcessingMode }.returns(false)
            every { it.suppressQstForAoe }.returns(false)
            every { it.suppressAoe }.returns(false)
            every { it.suppressHl7Fields }.returns(null)
            every { it.useBlankInsteadOfUnknown }.returns(null)
            every { it.convertTimestampToDateTime }.returns(null)
            every { it.truncateHDNamespaceIds }.returns(false)
            every { it.phoneNumberFormatting }.returns(Hl7Configuration.PhoneNumberFormatting.STANDARD)
            every { it.usePid14ForPatientEmail }.returns(false)
            every { it.reportingFacilityName }.returns(null)
            every { it.reportingFacilityId }.returns(null)
            every { it.reportingFacilityIdType }.returns(null)
            every { it.cliaForOutOfStateTesting }.returns(null)
            every { it.useOrderingFacilityName }.returns(Hl7Configuration.OrderingFacilityName.STANDARD)
            every { it.cliaForSender }.returns(mapOf("fake1" to "ABCTEXT123", "fake" to "10D1234567"))
            every { it.defaultAoeToUnknown }.returns(false)
        }
        val receiver = mockkClass(Receiver::class).also {
            every { it.translation }.returns(hl7Config)
            every { it.format }.returns(Report.Format.HL7)
            every { it.organizationName }.returns("ca-dph")
        }

        val testReport = csvSerializer.readExternal(schema, csvContent, listOf(TestSource), receiver).report ?: fail()
        val output = serializer.createMessage(testReport, 0)

        val cleanedMessage = reg.replace(output, "\r")
        val hapiMsg = parser.parse(cleanedMessage)
        val terser = Terser(hapiMsg)
        val msh41 = terser.get("/MSH-4-1")
        val msh42 = terser.get("/MSH-4-2")
        val msh43 = terser.get("/MSH-4-3")
        val msh10 = terser.get("/MSH-10")

        assertThat(msh41).equals("success")
        assertThat(msh42).equals("correctText-YES!")
        assertThat(msh43).equals("correctText-YES!")
        assertThat(msh10).equals("yeah/success")
    }
}