package com.gu.certw.services

import java.net.URI
import java.time.ZonedDateTime

import com.gu.certw.Env
import com.gu.certw.models.{ Certificate, Email }
import org.scalatest._

class CertificateServiceSpec extends FlatSpec with Matchers {

  def httpClient(result: String): HttpClient = new HttpClient {
    override def get(uri: URI) = result
  }

  val env = Env("app", "stack", "stage", "prism.com", "a@prism.com")

  "The certificate service" should "parse a list of certificates" in {
    val json = """{
                 |
                 |    "status": "success",
                 |    "lastUpdated": "2017-07-19T16:24:26.686Z",
                 |    "stale": false,
                 |    "staleSources": [ ],
                 |    "data": {
                 |        "acmCertificates": [
                 |            {
                 |                "arn": "arn:aws:acm:eu-west-1:7784783487:certificate/blah",
                 |                "domainName": "somedomain.com",
                 |                "certificateType": "AMAZON_ISSUED",
                 |                "status": "EXPIRED",
                 |                "issuer": "Amazon",
                 |                "inUseBy": [
                 |                    "arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh"
                 |                ],
                 |                "notBefore": "2016-09-06T00:00:00.000Z",
                 |                "createdAt": "2016-09-06T14:08:02.000Z",
                 |                "issuedAt": "2016-09-06T14:40:40.000Z",
                 |                "subject": "CN=somedomain2.com",
                 |                "keyAlgorithm": "RSA-2048",
                 |                "signatureAlgorithm": "SHA256WITHRSA",
                 |                "meta": {
                 |                    "href": "http://someDomain/acm-certificates/arn:aws:acm:eu-west-1:7784783487:certificate%2Fbleh",
                 |                    "origin": {
                 |                        "accountName": "someAccount",
                 |                        "ownerId": "someOwner",
                 |                        "region": "eu-west-1",
                 |                        "accountNumber": "7784783487",
                 |                        "vendor": "aws",
                 |                        "credentials": "arn:aws:iam::7784783487:role/PrismAccess-Prism"
                 |                    }
                 |                }
                 |            },
                 |            {
                 |                "arn": "arn:aws:acm:eu-west-1:7784783487:certificate/blah2",
                 |                "domainName": "somedomain2.com",
                 |                "certificateType": "AMAZON_ISSUED",
                 |                "status": "ISSUED",
                 |                "issuer": "Amazon",
                 |                "inUseBy": [
                 |                    "arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh2"
                 |                ],
                 |                "notBefore": "2016-09-06T00:00:00.000Z",
                 |                "notAfter": "2017-10-06T12:00:00.000Z",
                 |                "createdAt": "2016-09-06T14:08:02.000Z",
                 |                "issuedAt": "2016-09-06T14:40:40.000Z",
                 |                "subject": "CN=somedomain2.com",
                 |                "keyAlgorithm": "RSA-2048",
                 |                "signatureAlgorithm": "SHA256WITHRSA",
                 |                "meta": {
                 |                    "href": "http://somedomain/acm-certificates/arn:aws:acm:eu-west-1:7784783487:certificate%2Fbleh2",
                 |                    "origin": {
                 |                        "accountName": "someAccount2",
                 |                        "ownerId": "someOwner2",
                 |                        "region": "eu-west-1",
                 |                        "accountNumber": "7784783487",
                 |                        "vendor": "aws",
                 |                        "credentials": "arn:aws:iam::7784783487:role/PrismAccess-Prism"
                 |                    }
                 |                }
                 |            }
                 |        ]
                 |    }
                 |}""".stripMargin

    val expectedCertificates = List(
      Certificate(
        arn = "arn:aws:acm:eu-west-1:7784783487:certificate/blah",
        domainName = "somedomain.com",
        status = "EXPIRED",
        inUseBy = List("arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh"),
        notAfter = None,
        ownerId = "someOwner",
        accountName = "someAccount"),
      Certificate(
        arn = "arn:aws:acm:eu-west-1:7784783487:certificate/blah2",
        domainName = "somedomain2.com",
        status = "ISSUED",
        inUseBy = List("arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh2"),
        notAfter = Some(ZonedDateTime.parse("2017-10-06T12:00:00.000Z")),
        ownerId = "someOwner2",
        accountName = "someAccount2"))

    val certificates = CertificateService.listCertificates(env, httpClient(json))
    certificates shouldEqual expectedCertificates
  }

  it should "only keep the certificates that are about to expire (29 days)" in {
    val c1 = Certificate(
      arn = "arn:aws:acm:eu-west-1:7784783487:certificate/blah",
      domainName = "somedomain.com",
      status = "ISSUED",
      inUseBy = List("arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh"),
      notAfter = Some(ZonedDateTime.parse("2017-10-06T12:00:00.000Z")),
      ownerId = "someOwner",
      accountName = "someAccount")
    val c2 = c1.copy(notAfter = Some(ZonedDateTime.parse("2017-10-07T12:00:00.000Z")))

    val certificates = List(c1, c2)
    val expiringSoon = CertificateService.soonToExpire(certificates, now = ZonedDateTime.parse("2017-09-08T12:00:00.000Z"))
    expiringSoon shouldEqual List(c1)
  }

  it should "ignore certificate when their status is other than ISSUED and EXPIRED" in {
    val c1 = Certificate(
      arn = "arn:aws:acm:eu-west-1:7784783487:certificate/blah",
      domainName = "somedomain.com",
      status = "ISSUED",
      inUseBy = List("arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh"),
      notAfter = Some(ZonedDateTime.parse("2017-10-06T12:00:00.000Z")),
      ownerId = "someOwner",
      accountName = "someAccount")
    val c2 = c1.copy(status = "EXPIRED")
    val c3 = c1.copy(status = "SOMETHING ELSE")

    val certificates = List(c1, c2, c3)
    val expiringSoon = CertificateService.soonToExpire(certificates, now = ZonedDateTime.parse("2017-09-10T12:00:00.000Z"))
    expiringSoon shouldEqual List(c1, c2)
  }

  it should "generate emails" in {
    val c1 = Certificate(
      arn = "arn:aws:acm:eu-west-1:7784783487:certificate/blah",
      domainName = "somedomain.com",
      status = "ISSUED",
      inUseBy = List("arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh"),
      notAfter = Some(ZonedDateTime.parse("2017-10-06T12:00:00.000Z")),
      ownerId = "someOwner",
      accountName = "someAccount")

    val expectedMessage =
      """
      |The certificate for the domain somedomain.com is expired or is about to expire.
      |
      |AWS account: someAccount
      |ARN: arn:aws:acm:eu-west-1:7784783487:certificate/blah
      |Valid until: 2017-10-06T12:00Z
      |Status: ISSUED
      |Owned by: someOwner
      |Used by the following resources:
      | - arn:aws:elasticloadbalancing:eu-west-1:7784783487:loadbalancer/bleh
      |
      |Please check if this certificate is still needed and renew it as soon as possible
      |""".stripMargin

    val expectedSubject = "[Urgent] SSL/TLS Certificate for somedomain.com is about to expire"
    val emails = CertificateService.generateEmails(List(c1))
    emails shouldEqual List(Email("someOwner@theguardian.com", expectedSubject, expectedMessage))
  }
}
