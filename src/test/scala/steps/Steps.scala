package steps

import helpers.steps.StepsUtility._
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Minutes, Span}
import steps.Steps.{And, Given, Step, Then, When}
import uk.gov.nationalarchives.dp.client.Entities.Entity
import org.scalatest.matchers.should.Matchers._

import java.util.UUID
import scala.util.matching.Regex

class Steps extends Eventually {
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Minutes), interval = Span(1, Minutes))

  def judgmentSpecSteps(givenStep1: Step, whenStep1: Step, thenStep1: Step, andStep1: Step, andStep2: Step): List[Assertion] = {
    def parentPath(reference: UUID) =
      s"Records of the Supreme Court of Judicature and related courts/Supreme Court of Judicature: Court of Appeal: Judgments/name-$reference"

    def urlWithoutCourt(): String = {
      val id = (Math.random * 1000000).toInt
      s"https://example.com/id/2023/$id"
    }

    def validUri(): String = {
      val id = (Math.random * 1000000).toInt
      s"https://example.com/id/ewca/2023/$id"
    }

    val getValidUri = Map("valid" -> validUri, "valid URI with invalid court" -> urlWithoutCourt)
    val getCite = Map("valid" -> "[2023] EWCA 1421 (Comm)", "non-standard format" -> "invalid", "null" -> null)
    val skipSeriesLookup = Map("true" -> true, "false" -> false)

    val (reference, uriWithExpectedCourt) =
      Given("^A judgment with a (.*) URI with an? (.*) court and a (.*) cite").run { regexString =>
        givenStep1.logMessage()
        val givenMatches = getAndValidateRegexMatches(givenStep1.message, regexString, 3)
        val courtType = givenMatches(1)
        val uri = getValidUri(givenMatches.head)
        val uriWithExpectedCourt = if (courtType == "invalid") uri.replace("ewca", courtType) else uri
        val cite = getCite(givenMatches(2))

        val reference: UUID = createJudgment(Option(cite), Option(uriWithExpectedCourt))
        (reference, uriWithExpectedCourt)
      }

    When("^The judgment ingest is triggered and skipSeriesLookup is set to (.*)").run { regexString =>
      whenStep1.logMessage()
      val whenMatches = getAndValidateRegexMatches(thenStep1.message, regexString, 1)
      sendSQSMessage(reference, skipSeriesLookup = skipSeriesLookup(whenMatches.head))
    }

    val entity: Entity =
      Then("^The judgment will be in Preservica").run { regexString =>
        thenStep1.logMessage()
        val stepMessage = whenStep1.message
        thenStep1.messageLogger(stepMessage)
        getAndValidateRegexMatches(andStep1.message, regexString, 0)
        eventually { getEntity(s"$reference.docx") }
      }

    And("^The parent path will be '(.*)'").run { regexString =>
      andStep1.logMessage()
      val and1Matches = getAndValidateRegexMatches(andStep1.message, regexString, 1)
      val expectedParentPath = and1Matches.head.replace("{reference}", parentPath(reference))
      List(getParentPath(entity.parent) should equal(expectedParentPath))
    }

    And("^(.*) identifiers? will be set correctly on the parent folder: (.*)").run { regexString =>
      andStep2.logMessage()
      val and2Matches = getAndValidateRegexMatches(andStep2.message, regexString, 2)
      val identifiers = getIdentifiers(entity.parent)
      val numberOfIdentifiers = and2Matches.head
      val expectedIdentifiers: Map[String, String] =
        and2Matches(1)
          .replace("{URI}", uriWithExpectedCourt)
          .split(",")
          .map { identifier =>
            val identifierAndValue = identifier.split("=")
            (identifierAndValue.head.strip(), identifierAndValue(1).strip())
          }
          .toMap

      identifiers.size should equal(numberOfIdentifiers.toInt)
      identifiers.map { case (name, value) => value should equal(expectedIdentifiers(name)) }.toList
    }
  }

  private def getAndValidateRegexMatches(step: String, stepRegex: Regex, numExpectedMatches: Int): List[String] = {
    val regexMatches = stepRegex.findAllIn(step).matchData.toList.flatMap(_.subgroups)
    val numOfMatches = regexMatches.length

    if (numOfMatches != numExpectedMatches)
      throw new Exception(
        s"There should be $numExpectedMatches matches but instead are $numOfMatches; please check the Step.\n" +
          s"Step :  $step\n" +
          s"Regex: $stepRegex"
      )
    else regexMatches
  }
}

object Steps {
  case class Step(message: String, messageLogger: String => Unit) {
    def logMessage(): Unit = messageLogger(message)
  }

  case class Given(regexAsString: String) {
    def run(cb: Regex => (UUID, String)): (UUID, String) = cb(regexAsString.r)
  }

  case class When(regexAsString: String) {
    def run(cb: Regex => Unit): Unit = cb(regexAsString.r)
  }

  case class Then(regexAsString: String) {
    def run(cb: Regex => Entity): Entity = cb(regexAsString.r)
  }

  case class And(regexAsString: String) {
    def run(cb: Regex => List[Assertion]): List[Assertion] = cb(regexAsString.r)
  }
}
