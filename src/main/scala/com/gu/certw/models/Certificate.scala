package com.gu.certw.models

import java.time.ZonedDateTime

case class Certificate(
  arn: String,
  domainName: String,
  status: String,
  inUseBy: List[String],
  notAfter: Option[ZonedDateTime],
  ownerId: String,
  accountName: String)
