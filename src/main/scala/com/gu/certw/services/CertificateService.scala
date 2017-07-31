package com.gu.certw.services

import java.net.URI
import java.time.ZonedDateTime

import com.amazonaws.services.simpleemail.model._
import com.gu.certw.{ Env, Logging }
import com.gu.certw.models._
import play.api.libs.functional.syntax._
import play.api.libs.json.{ JsPath, Json, Reads }

object CertificateService extends Logging {

  private implicit val prismAcmCertificateParser: Reads[Certificate] = (
    (JsPath \ "arn").read[String] and
    (JsPath \ "domainName").read[String] and
    (JsPath \ "status").read[String] and
    (JsPath \ "inUseBy").read[List[String]] and
    (JsPath \ "notAfter").readNullable[ZonedDateTime] and
    (JsPath \ "meta" \ "origin" \ "ownerId").read[String] and
    (JsPath \ "meta" \ "origin" \ "accountName").read[String])(Certificate.apply _)

  def listCertificates(env: Env, httpClient: HttpClient): List[Certificate] = {
    val body = httpClient.get(new URI(s"https://${env.prismDomain}/acm-certificates"))
    (Json.parse(body) \ "data" \ "acmCertificates").as[List[Certificate]]
  }

  private val activeOrExpired = Set("ISSUED", "EXPIRED")

  def soonToExpire(certificates: List[Certificate], now: ZonedDateTime): List[Certificate] = certificates
    .filter(cert => activeOrExpired.contains(cert.status))
    .filter(_.notAfter.forall(_.isBefore(now.plusDays(29))))

  def generateEmails(certificates: List[Certificate]): List[Email] = {
    certificates.map { certificate =>
      val to = s"${certificate.ownerId}@theguardian.com"
      val resources = if (certificate.inUseBy.isEmpty) {
        "This certificate isn't currently used by any resources (ELB, ALB ...)"
      } else {
        val resourceList = certificate.inUseBy.map(r => s" - $r").mkString("\n")
        s"Used by the following resources:\n$resourceList"
      }
      val message =
        s"""
           |The certificate for the domain ${certificate.domainName} is expired or is about to expire.
           |
           |AWS account: ${certificate.accountName}
           |ARN: ${certificate.arn}
           |Valid until: ${certificate.notAfter.getOrElse("unknown")}
           |Status: ${certificate.status}
           |Owned by: ${certificate.ownerId}
           |$resources
           |
           |Please check if this certificate is still needed and renew it as soon as possible
           |""".stripMargin

      val subject = s"[Action required] SSL/TLS Certificate for ${certificate.domainName} is about to expire"

      Email(to, subject, message)
    }
  }

}
