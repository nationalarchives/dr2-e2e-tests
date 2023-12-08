package uk.gov.nationalarchives

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import steps.Steps

class JudgmentSpec extends AnyFlatSpec with ParallelTestExecution {
  private def IngestWorkflow(Given: String, When: String, Then: String, And: String, AndFinally: String): List[Assertion] =
    new Steps().judgmentSpecSteps(Given, When, Then, And, AndFinally)

  "A judgment with a valid uri and a null cite" should "ingest into Preservica with 1 identifier" in {
    IngestWorkflow(
      Given = "A judgment with a valid URI with a valid court and a null cite",
      When = "The judgment ingest is triggered and skipSeriesLookup is set to false",
      Then = "The judgment will be in Preservica",
      And = "The parent path will be '{reference}'",
      AndFinally = "1 identifier will be set correctly on the parent folder: SourceId={URI}"
    )
  }

  "A judgment with a valid uri and a cite" should "ingest into Preservica with 4 identifiers" in {
    IngestWorkflow(
      Given = "A judgment with a valid URI with a valid court and a valid cite",
      When = "The judgment ingest is triggered and skipSeriesLookup is set to false",
      Then = "The judgment will be in Preservica",
      And = "The parent path will be '{reference}'",
      AndFinally = "4 identifiers will be set correctly on the parent folder:" +
        "SourceId={URI}, URI={URI}, Cite=[2023] EWCA 1421 (Comm), Code=[2023] EWCA 1421 (Comm)"
    )
  }

  "A judgment with a invalid court in the uri and the skipSeriesLookup parameter set" should "ingest into Preservica in the Unknown folder" in {
    IngestWorkflow(
      Given = "A judgment with a valid URI with an invalid court and a non-standard format cite",
      When = "The judgment ingest is triggered and skipSeriesLookup is set to true",
      Then = "The judgment will be in Preservica",
      And = s"The parent path will be 'Unknown/Court Documents (court not matched)'",
      AndFinally = "3 identifiers will be set correctly on the parent folder: " +
        "SourceId=Court Documents (court not matched), Cite=invalid, Code=invalid"
    )
  }

  "A judgment with a missing court in the uri" should "ingest into Preservica in the Unknown folder" in {
    IngestWorkflow(
      Given = "A judgment with a valid URI with an invalid court and a null cite",
      When = "The judgment ingest is triggered and skipSeriesLookup is set to false",
      Then = "The judgment will be in Preservica",
      And = "The parent path will be 'Unknown/Court Documents (court unknown)'",
      AndFinally = "1 identifier will be set correctly on the parent folder: SourceId=Court Documents (court unknown)"
    )
  }
}
