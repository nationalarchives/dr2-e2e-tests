package uk.gov.nationalarchives

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Sync}
import cats.implicits._
import fs2.Stream
import fs2.interop.reactivestreams._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Printer}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.output.ByteArrayOutputStream
import org.docx4j.Docx4J
import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.dp.client.ContentClient.{SearchField, SearchQuery}
import uk.gov.nationalarchives.dp.client.Entities.Entity
import uk.gov.nationalarchives.dp.client.EntityClient.StructuralObject
import uk.gov.nationalarchives.dp.client.fs2.Fs2Client.{contentClient, entityClient}

import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID

object Steps {
  private val url = sys.env("PRESERVICA_API_URL")
  private val secretName = sys.env("PRESERVICA_SECRET_NAME")
  private val environment = sys.env("ENVIRONMENT")
  private val accountId = sys.env("ACCOUNT_ID")
  private val bucket = s"$environment-ingest-parsed-court-document-test-input"
  private val sqsUrl = s"https://sqs.eu-west-2.amazonaws.com/$accountId/$environment-ingest-parsed-court-document-event-handler"

  val daS3Client: DAS3Client[IO] = DAS3Client()
  val daSQSClient: DASQSClient[IO] = DASQSClient()

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  private def createDocx(reference: UUID): Array[Byte] = {
    val wordMLPackage = WordprocessingMLPackage.createPackage()
    val mdp = wordMLPackage.getMainDocumentPart
    mdp.addParagraphOfText(reference.toString)
    val os = new ByteArrayOutputStream()
    Docx4J.save(wordMLPackage, os, Docx4J.FLAG_SAVE_ZIP_FILE)
    os.toByteArray
  }

  private def createChecksum(docxBytes: Array[Byte]): String = {
    val checksum = MessageDigest.getInstance("SHA-256").digest(docxBytes)
    checksum.map("%02x" format _).mkString
  }

  private def createMetadata(cite: Option[String], uri: Option[String], reference: UUID, checksum: String): Array[Byte] = {
    val prefix = if (cite.isEmpty && uri.isDefined) "Press Summary of " else ""
    val parser = Parser(uri.map(u => s"$u/$reference"), cite, Option(s"${prefix}name-$reference"))
    val tre = TREParams(reference.toString, Payload(s"$reference.docx"))
    val tdr = TDRParams(checksum, "e2e-tests", reference.toString, OffsetDateTime.now())
    val metadata = TREMetadata(TREMetadataParameters(parser, tre, tdr))
    metadata.asJson.printWith(Printer.noSpaces).getBytes
  }

  private def parentPath(entityRef: Option[UUID], paths: List[String] = Nil): IO[List[String]] = {
    if (entityRef.isEmpty) {
      IO(paths)
    } else {
      for {
        entityClient <- entityClient(url, secretName)
        entity <- entityClient.getEntity(entityRef.get, StructuralObject)
        res <- parentPath(entity.parent, entity.title.getOrElse("") :: paths)
      } yield res
    }
  }

  def getParentPath(entityRef: Option[UUID]): String = {
    parentPath(entityRef, Nil).unsafeRunSync().mkString("/")
  }

  def getIdentifiers(potentialRef: Option[UUID]): Map[String, String] = (for {
    ref <- IO.fromOption(potentialRef)(new Exception("No parent ref provided"))
    entityClient <- entityClient(url, secretName)
    entity <- entityClient.getEntity(ref, StructuralObject)
    identifiers <- entityClient.getEntityIdentifiers(entity)
  } yield identifiers.map(id => id.identifierName -> id.value).toMap).unsafeRunSync()

  def getEntity(title: String): Entity = (for {
    contentClient <- contentClient(url, secretName)
    entityClient <- entityClient(url, secretName)
    res <- contentClient.searchEntities(SearchQuery("", SearchField("xip.title", List(title)) :: Nil))
    entities <- res.map(entity => entityClient.getEntity(entity.ref, entity.entityType.get)).sequence
    entity <- IO.fromOption(entities.headOption)(new Exception("Entity not found"))
    _ <- Logger[IO].info(s"Entity ${entity.ref} found for title $title")
  } yield entity).unsafeRunSync

  def sendSQSMessage(reference: UUID, skipSeriesLookup: Boolean = false): SendMessageResponse = {
    val parameter: Parameter = if (skipSeriesLookup) {
      SQSParameterWithSkipLookup("OK", reference.toString, bucket, s"$reference.tar.gz", skipSeriesLookup = true)
    } else {
      SQSParameter("OK", reference.toString, bucket, s"$reference.tar.gz")
    }
    val sqsMessage = SQSMessage(parameter)
    daSQSClient.sendMessage(sqsUrl)(sqsMessage)
  }.unsafeRunSync

  def createJudgment(cite: Option[String], uri: Option[String]): UUID = {
    val reference = UUID.randomUUID()
    val docxBytes = createDocx(reference)
    val checksum = createChecksum(docxBytes)
    val metadataBytes = createMetadata(cite, uri, reference, checksum)
    val tarBytes = createTarFile(reference, docxBytes, metadataBytes)
    for {
      _ <- Logger[IO].info(s"Creating judgment with reference $reference")
      reference <- Stream.emits[IO, Byte](tarBytes).chunks.map(_.toByteBuffer).toUnicastPublisher.use { publisher =>
        daS3Client.upload(bucket, s"$reference.tar.gz", tarBytes.length, publisher).map(_ => reference)
      }
    } yield reference
  }.unsafeRunSync()

  def createTarFile(reference: UUID, docxBytes: Array[Byte], metadataBytes: Array[Byte]): Array[Byte] = {
    val os = new ByteArrayOutputStream()
    val gzipOs = new GzipCompressorOutputStream(os)
    val tarOut = new TarArchiveOutputStream(gzipOs)

    def addTarEntry(name: String, bytes: Array[Byte]): Unit = {
      val tarEntry = new TarArchiveEntry(name)
      tarEntry.setSize(bytes.length)
      tarOut.putArchiveEntry(tarEntry)
      tarOut.write(bytes)
      tarOut.closeArchiveEntry()
    }

    addTarEntry(s"$reference/TRE-$reference-metadata.json", metadataBytes)
    addTarEntry(s"$reference/$reference.docx", docxBytes)
    gzipOs.close()
    os.close()
    os.toInputStream.readAllBytes()
  }

  implicit val parameterEncoder: Encoder[Parameter] = {
    case p: SQSParameter               => p.asJson
    case p: SQSParameterWithSkipLookup => p.asJson
  }
  sealed trait Parameter
  case class SQSParameter(status: String, reference: String, s3Bucket: String, s3Key: String) extends Parameter
  case class SQSParameterWithSkipLookup(
      status: String,
      reference: String,
      s3Bucket: String,
      s3Key: String,
      skipSeriesLookup: Boolean
  ) extends Parameter

  case class SQSMessage(parameters: Parameter)

  case class TREInputParameters(status: String, reference: String, s3Bucket: String, s3Key: String)

  case class TREInput(parameters: TREInputParameters)

  case class TREMetadata(parameters: TREMetadataParameters)

  case class Parser(
      uri: Option[String],
      cite: Option[String] = None,
      name: Option[String],
      attachments: List[String] = Nil,
      `error-messages`: List[String] = Nil
  )

  case class Payload(filename: String)

  case class TREParams(reference: String, payload: Payload)

  case class TDRParams(
      `Document-Checksum-sha256`: String,
      `Source-Organization`: String,
      `Internal-Sender-Identifier`: String,
      `Consignment-Export-Datetime`: OffsetDateTime
  )

  case class TREMetadataParameters(PARSER: Parser, TRE: TREParams, TDR: TDRParams)
}
