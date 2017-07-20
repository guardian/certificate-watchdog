package com.gu.certw

import java.time.ZonedDateTime

import com.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.simpleemail.{ AmazonSimpleEmailService, AmazonSimpleEmailServiceAsyncClientBuilder }
import com.gu.certw.services.{ CertificateService, DefaultHttpClient }

case class Env(app: String, stack: String, stage: String, prismDomain: String, senderEmail: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"),
    Option(System.getenv("PrismDomain")).get,
    Option(System.getenv("SenderEmail")).get)
}

object Lambda extends Logging {

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("deployTools"),
    DefaultAWSCredentialsProviderChain.getInstance())

  val ses: AmazonSimpleEmailService = AmazonSimpleEmailServiceAsyncClientBuilder.standard()
    .withCredentials(credentials)
    .withRegion(Regions.EU_WEST_1)
    .build()

  val httpClient = new DefaultHttpClient
  val certificateService = new CertificateService(httpClient, ses)

  def handler(): Unit = {
    logger.info(s"Starting ${Env()}")

    val certificates = certificateService.listCertificates
    val expireSoon = certificateService.soonToExpire(certificates, ZonedDateTime.now())
    val arns = expireSoon.map(_.arn)
    logger.info(s"Certificates about to expire: $arns")
    certificateService.sendEmails(expireSoon)
  }
}

object TestIt {
  def main(args: Array[String]): Unit = {
    Lambda.handler()
  }
}
