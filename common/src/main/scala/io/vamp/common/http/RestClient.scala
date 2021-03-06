package io.vamp.common.http

import java.util.concurrent.ExecutionException

import com.ning.http.client.{ AsyncCompletionHandler, Response }
import com.typesafe.scalalogging.Logger
import dispatch._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect._

case class RestClientException(statusCode: Option[Int], message: String) extends RuntimeException(message) {}

object RestClient {

  private val logger = Logger(LoggerFactory.getLogger(RestClient.getClass))

  object Method extends Enumeration {
    val HEAD, GET, POST, PUT, DELETE, PATCH, TRACE, OPTIONS = Value
  }

  val acceptEncodingIdentity: (String, String) = "accept-encoding" -> "identity"

  val jsonHeaders: List[(String, String)] = List("Accept" -> "application/json", "Content-Type" -> "application/json")

  def get[A](url: String, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.GET, url, None, headers, logError)
  }

  def post[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.POST, url, body, headers, logError)
  }

  def put[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.PUT, url, body, headers, logError)
  }

  def delete(url: String, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext) = {
    http(Method.DELETE, url, None, headers, logError)
  }

  def http[A](method: Method.Value, url: String, body: Any, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {

    val requestLog = s"[${method.toString} $url]"

    val requestWithUrl = dispatch.url(url).setMethod(method.toString)
    val requestWithHeaders = headers.foldLeft(requestWithUrl)((http, header) ⇒ http.setHeader(header._1, header._2))
    val requestWithBody = bodyAsString(body) match {
      case Some(some) ⇒
        logger.trace(s"req $requestLog - $some")
        requestWithHeaders.setBody(some)
      case None ⇒
        logger.trace(s"req $requestLog")
        requestWithHeaders
    }

    Http(requestWithBody.toRequest -> new AsyncCompletionHandler[A] {
      def onCompleted(response: Response) = response.getStatusCode match {
        case status if status / 100 == 2 && (classTag[A].runtimeClass == classOf[Nothing] || classTag[A].runtimeClass == classOf[String]) ⇒
          val body = response.getResponseBody
          logger.trace(s"rsp $requestLog - $body")
          body.asInstanceOf[A]

        case status if status / 100 == 2 ⇒
          val json = dispatch.as.json4s.Json(response)
          logger.trace(s"rsp $requestLog - ${compact(render(json))}")
          json.extract[A](formats, mf)

        case status ⇒
          val message = s"rsp $requestLog - unexpected status code: $status"
          if (logError) logger.error(message)
          logger.trace(s"$message, for response: ${response.getResponseBody}")
          throw RestClientException(Some(status), response.getResponseBody)
      }
    }).recover {
      case exception: RestClientException ⇒ throw exception
      case exception: ExecutionException if exception.getCause != null && exception.getCause.getClass == classOf[RestClientException] ⇒ throw exception.getCause
      case exception ⇒
        val message = s"rsp $requestLog - exception: ${exception.getMessage}"

        if (logError) {
          logger.error(message)
          logger.trace(message, exception)
        }

        throw RestClientException(None, exception.getMessage).initCause(if (exception.getCause != null) exception.getCause else exception)
    }
  }

  private def bodyAsString(body: Any)(implicit formats: Formats): Option[String] = body match {
    case string: String                            ⇒ Some(string)
    case Some(string: String)                      ⇒ Some(string)
    case Some(some: AnyRef)                        ⇒ Some(write(some))
    case any: AnyRef if any != null && any != None ⇒ Some(write(any))
    case any if any != null && any != None         ⇒ Some(any.toString)
    case _                                         ⇒ None
  }
}
