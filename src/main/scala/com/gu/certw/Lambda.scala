package com.gu.certw

import java.time.ZonedDateTime

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions.getCurrentRegion
import com.amazonaws.services.simpleemail.model._
import com.amazonaws.services.simpleemail.{ AmazonSimpleEmailService, AmazonSimpleEmailServiceAsyncClientBuilder }
import com.gu.certw.models.Email
import com.gu.certw.services.{ CertificateService, DefaultHttpClient }

case class Env(app: String, stack: String, stage: String, prismDomain: String, senderEmail: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage"
}

object Lambda extends Logging {

  val envVars: Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"),
    Option(System.getenv("PrismDomain")).get,
    Option(System.getenv("SenderEmail")).get
  )

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("deployTools"),
    DefaultAWSCredentialsProviderChain.getInstance())

  val ses: AmazonSimpleEmailService = AmazonSimpleEmailServiceAsyncClientBuilder.standard()
    .withCredentials(credentials)
    .withRegion(getCurrentRegion.getName)
    .build()

  val httpClient = new DefaultHttpClient

  def handler(): Unit = {
    logger.info(s"Starting $envVars")

    val certificates = CertificateService.listCertificates(envVars, httpClient)
    val expireSoon = CertificateService.soonToExpire(certificates, ZonedDateTime.now())
    val arns = expireSoon.map(_.arn)
    logger.info(s"Certificates about to expire: $arns")

    val emails = CertificateService.generateEmails(expireSoon)
    sendEmails(ses, emails)
  }

  def sendEmails(ses: AmazonSimpleEmailService, emails: List[Email]): Unit = {
    emails.foreach { email =>
      logger.info(s"Sending email ${email.subject}")

      val request = new SendEmailRequest()
        .withDestination(new Destination().withToAddresses(email.to))
        .withSource(envVars.senderEmail)
        .withMessage(new Message()
          .withSubject(new Content(email.subject))
          .withBody(new Body(new Content(email.message))))

      ses.sendEmail(request)
    }

  }
}

object TestIt {
  def main(args: Array[String]): Unit = {
    Lambda.handler()
  }
}
