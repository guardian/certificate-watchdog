package com.gu.certw.services

import java.net.URI

import scala.io.Source

trait HttpClient {
  def get(uri: URI): String
}

class DefaultHttpClient extends HttpClient {
  override def get(uri: URI): String = {
    Source.fromInputStream(uri.toURL.openStream()).mkString
  }
}
