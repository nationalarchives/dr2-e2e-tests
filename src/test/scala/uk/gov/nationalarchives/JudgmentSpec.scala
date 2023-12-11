package uk.gov.nationalarchives

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import steps.Steps
import steps.Steps.Step

class JudgmentSpec extends AnyFlatSpec with GivenWhenThen with ParallelTestExecution {
  "A judgment with a valid uri and a null cite" should "ingest into Preservica with 1 identifier" in {
    IngestWorkflow(
      _Given = "A judgment with a valid URI with a valid court and a null cite",
      _When = "The judgment ingest is triggered and skipSeriesLookup is set to false",
      _Then = "The judgment will be in Preservica",
      _And = "The parent path will be '{reference}'",
      _AndFinally = "1 identifier will be set correctly on the parent folder: SourceId={URI}"
    )
  }

  "A judgment with a valid uri and a cite" should "ingest into Preservica with 4 identifiers" in {
    IngestWorkflow(
      _Given = "A judgment with a valid URI with a valid court and a valid cite",
      _When = "The judgment ingest is triggered and skipSeriesLookup is set to false",
      _Then = "The judgment will be in Preservica",
      _And = "The parent path will be '{reference}'",
      _AndFinally = "4 identifiers will be set correctly on the parent folder:" +
        "SourceId={URI}, URI={URI}, Cite=[2023] EWCA 1421 (Comm), Code=[2023] EWCA 1421 (Comm)"
    )
  }

  "A judgment with a invalid court in the uri and the skipSeriesLookup parameter set" should "ingest into Preservica in the Unknown folder" in {
    IngestWorkflow(
      _Given = "A judgment with a valid URI with an invalid court and a non-standard format cite",
      _When = "The judgment ingest is triggered and skipSeriesLookup is set to true",
      _Then = "The judgment will be in Preservica",
      _And = s"The parent path will be 'Unknown/Court Documents (court not matched)'",
      _AndFinally = "3 identifiers will be set correctly on the parent folder: " +
        "SourceId=Court Documents (court not matched), Cite=invalid, Code=invalid"
    )
  }

  "A judgment with a missing court in the uri" should "ingest into Preservica in the Unknown folder" in {
    IngestWorkflow(
      _Given = "A judgment with a valid URI with an invalid court and a null cite",
      _When = "The judgment ingest is triggered and skipSeriesLookup is set to false",
      _Then = "The judgment will be in Preservica",
      _And = "The parent path will be 'Unknown/Court Documents (court unknown)'",
      _AndFinally = "1 identifier will be set correctly on the parent folder: SourceId=Court Documents (court unknown)"
    )
  }

  private def IngestWorkflow(_Given: String, _When: String, _Then: String, _And: String, _AndFinally: String): Unit =
    new Steps().judgmentSpecSteps(
      Step(_Given, g => Given(g)),
      Step(_When, w => When(w)),
      Step(_Then, t => Then(t)),
      Step(_And, a => And(a)),
      Step(_AndFinally, af => And(af))
    )
}
