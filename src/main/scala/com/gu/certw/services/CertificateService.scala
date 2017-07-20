package com.gu.certw.services

import java.net.URI
import java.time.ZonedDateTime

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.model._
import com.gu.certw.{Env, Logging}
import com.gu.certw.models._
import play.api.libs.functional.syntax._
import play.api.libs.json.{ JsPath, Json, Reads }

class CertificateService(httpClient: HttpClient, ses: AmazonSimpleEmailService) extends Logging {

  private implicit val prismAcmCertificateParser: Reads[Certificate] = (
    (JsPath \ "arn").read[String] and
    (JsPath \ "domainName").read[String] and
    (JsPath \ "status").read[String] and
    (JsPath \ "inUseBy").read[List[String]] and
    (JsPath \ "notAfter").readNullable[ZonedDateTime] and
    (JsPath \ "meta" \ "origin" \ "ownerId").read[String] and
    (JsPath \ "meta" \ "origin" \ "accountName").read[String])(Certificate.apply _)

  def listCertificates: List[Certificate] = {
    val body = httpClient.get(new URI(s"https://${Env().prismDomain}/acm-certificates"))
    (Json.parse(body) \ "data" \ "acmCertificates").as[List[Certificate]]
  }

  private val activeOrExpired = Set("ISSUED", "EXPIRED")

  def soonToExpire(certificates: List[Certificate], now: ZonedDateTime): List[Certificate] = certificates
    .filter(cert => activeOrExpired.contains(cert.status))
    .filter(_.inUseBy.nonEmpty)
    .filter(_.notAfter.forall(_.isBefore(now.plusDays(29))))

  def sendEmails(certificates: List[Certificate]): Unit = {
    certificates.foreach { certificate =>
      val to = s"${certificate.ownerId}@theguardian.com"
      val message =
        s"""
           |The certificate for the domain ${certificate.domainName} is expired or is about to expire.
           |
           |AWS account: ${certificate.accountName}
           |ARN: ${certificate.arn}
           |Valid until: ${certificate.notAfter.getOrElse("unknown")}
           |Status: ${certificate.status}
           |Owned by: ${certificate.ownerId}
           |Used by the following resources:
           |${certificate.inUseBy.map(r => s" - $r").mkString("\n")}
           |
           |Please check if that certicate is still needed and renew it as soon as possible
         """.stripMargin

      logger.info(s"Sending email about ${certificate.domainName} ${certificate.arn}")

      val request = new SendEmailRequest()
        .withDestination(new Destination().withToAddresses(to))
        .withSource(Env().senderEmail)
        .withMessage(new Message()
          .withSubject(new Content(s"[Urgent] SSL/TLS Certificate for ${certificate.domainName} is about to expire"))
          .withBody(new Body(new Content(message))))

      ses.sendEmail(request)
    }

  }

}
