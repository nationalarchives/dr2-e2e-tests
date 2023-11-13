package uk.gov.nationalarchives

import Steps._
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.time.{Minutes, Span}

import java.util.UUID

class JudgmentSpec extends AnyFlatSpec with GivenWhenThen with ParallelTestExecution with Eventually {
  def urlWithoutCourt(): String = {
    val id = (Math.random * 1000000).toInt.toString
    s"https://example.com/id/2023/$id"
  }
  def validUri(): String = {
    val id = (Math.random * 1000000).toInt.toString
    s"https://example.com/id/ewca/2023/$id"
  }

  private def parentPath(reference: UUID) =
    s"Records of the Supreme Court of Judicature and related courts/Supreme Court of Judicature: Court of Appeal: Judgments/name-$reference"

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(15, Minutes), interval = Span(1, Minutes))

  "A judgment with a valid uri and a null cite" should "ingest into Preservica" in {
    Given("A judgment with a valid URI and a null cite")
    val uri = validUri()
    val reference = createJudgment(None, Option(uri))

    When("The judgment ingest is triggered")
    sendSQSMessage(reference)

    Then("The judgment will be in Preservica")
    val entity = eventually {
      getEntity(s"$reference.docx")
    }

    And(s"The parent path will be correct")
    getParentPath(entity.parent) should equal(parentPath(reference))

    And("The SourceId identifiers will be set correctly")
    val identifiers = getIdentifiers(entity.parent)
    identifiers.size should equal(1)
    identifiers("SourceID") should equal(uri)
  }

  "A judgment with a valid uri and a valid cite" should "ingest into Preservica" in {
    Given("A judgment with a valid URI and a valid cite")
    val uri = validUri()
    val reference = createJudgment(Option("ewca"), Option(uri))

    When("The judgment ingest is triggered")
    sendSQSMessage(reference)

    Then("The judgment will be in Preservica")
    val entity = eventually {
      getEntity(s"$reference.docx")
    }
    And(s"The parent path will be correct")
    getParentPath(entity.parent) should equal(parentPath(reference))

    And("The SourceId, Cite and Code identifiers will be set correctly on the parent folder")
    val identifiers = getIdentifiers(entity.parent)
    identifiers.size should equal(4)
    identifiers("SourceID") should equal(uri)
    identifiers("URI") should equal(uri)
    identifiers("Cite") should equal("ewca")
    identifiers("Code") should equal("ewca")
  }

  "A judgment with a invalid court in the uri and the skipSeriesLookup parameter set" should "ingest into Preservica in the Unknown folder" in {
    Given("A judgment with a valid URI but an invalid court and an invalid cite")
    val uri = validUri().replace("ewca", "invalid")
    val reference = createJudgment(Option("invalid"), Option(uri))

    When("The judgment ingest is triggered")
    sendSQSMessage(reference, skipSeriesLookup = true)

    Then("The judgment will be in Preservica")
    val entity = eventually {
      getEntity(s"$reference.docx")
    }
    And(s"The parent path will be correct")
    getParentPath(entity.parent) should equal("Unknown/Court Documents (court not matched)")

    And("The SourceId, Cite and Code identifiers will be set correctly on the parent folder")
    val identifiers = getIdentifiers(entity.parent)
    identifiers.size should equal(3)
    identifiers("SourceID") should equal("Court Documents (court not matched)")
    identifiers("Cite") should equal("invalid")
    identifiers("Code") should equal("invalid")
  }

  "A judgment with a missing court in the uri" should "ingest into Preservica in the Unknown folder" in {
    Given("A judgment with a valid URI but an invalid court and an invalid cite")
    val uri = urlWithoutCourt()
    val reference = createJudgment(None, Option(uri))

    When("The judgment ingest is triggered")
    sendSQSMessage(reference)

    Then("The judgment will be in Preservica")
    val entity = eventually {
      getEntity(s"$reference.docx")
    }
    And(s"The parent path will be correct")
    getParentPath(entity.parent) should equal("Unknown/Court Documents (court unknown)")

    And("The SourceId, Cite and Code identifiers will be set correctly on the parent folder")
    val identifiers = getIdentifiers(entity.parent)
    identifiers.size should equal(1)
    identifiers("SourceID") should equal("Court Documents (court unknown)")
  }
}
