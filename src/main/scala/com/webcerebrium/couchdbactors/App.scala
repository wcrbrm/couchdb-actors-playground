package com.webcerebrium.couchdbactors

import scalaj.http.{ Http, HttpResponse }
import ujson._
import scala.util.Properties
import akka.actor.{ ActorSystem, Actor, ActorRef, Props, PoisonPill, ActorLogging }
import akka.stream.ActorMaterializer
import kamon._
import com.typesafe.config.ConfigFactory

// to run: amm -s --no-remote-logging DbBackup.sc
case class CouchDatabase(host: String = "127.0.0.1", port: Int = 5984, protocol: String = "http", username: String = "", password: String = "") {
   def connUrl: String = if (!username.isEmpty) s"${protocol}://${username}:${password}@${host}:${port}" else s"${protocol}://${host}:${port}"
   def databases: List[String] = {
     val response: HttpResponse[String] = Http(s"${connUrl}/_all_dbs").asString
     val Js.Arr(body) = ujson.read(response.body)
     val arrBuffer = for { 
        Js.Str(id) <- body.arr
        if (!id.startsWith("_"))
     } yield id
     arrBuffer.toList
   }
   def forEachDocsChunk(database: String, batchSize: Int = 100, handler:(Js.Arr, Int, Int) => Unit) = {
     var offset: Int = 0
     var stop: Boolean = false
     do {
        val url = s"${connUrl}/${database}/_all_docs?limit=${batchSize}&skip=${offset}"
        val response: HttpResponse[String] = Http(url).asString
        val Js.Obj(body) = ujson.read(response.body)
        val rows = body.get("rows").get.arr
        val total_rows = body.get("total_rows").get.num.toInt
        val realOffset = body.get("offset").get.num.toInt
        handler(rows, realOffset, total_rows)
        offset += batchSize
        if (rows.arr.length == 0 || offset > total_rows) stop = true
     } while (!stop)
   }
}

object Couch extends CouchDatabase(
  Properties.envOrElse("COUCHDB_HOST", "127.0.0.1"),
  Integer.parseInt(Properties.envOrElse("COUCHDB_PORT", "5984")),
  Properties.envOrElse("COUCHDB_PROTOCOL", "http"),
  Properties.envOrElse("COUCHDB_USERNAME", ""),
  Properties.envOrElse("COUCHDB_PASSWORD", "")
)

object DbProcessorActor {
   case object Download
   case class Chunk(rows: Js.Arr, offset: Int, total: Int)
   case object DatabaseDone
}
class DbProcessorActor(databaseId: String) extends Actor with ActorLogging {
  import DbProcessorActor._
  var batchSize: Int = 50
  var processed: Int = 0
  def receive = {
    case Chunk(rows: Js.Arr, offset: Int, total: Int) => {
        // log.info(s"${self.path} processing chunk=${offset}/${total} database=${databaseId}")
        processed += rows.arr.size
    }
    case DatabaseDone => {
        log.info(s"${self.path} finished: ${databaseId} ${processed}")
        self ! PoisonPill
    }
    case Download => {
        log.info(s"${self.path} received task to take '$databaseId'")
        Couch.forEachDocsChunk(databaseId, batchSize, (rows: Js.Arr, offset: Int, total: Int) => {
            // log.info(s"${self.path} db=$databaseId, chunk=${offset}/${total}")
            self ! Chunk(rows, offset, total)
            if ((batchSize + offset) >= total) self ! DatabaseDone
        })
    }
  }
}

// val config = ConfigFactory.parseFile(new java.io.File("./application.conf"))
// implicit val system = ActorSystem("dblist", config)

object ActorsApp extends App {

  implicit val system = ActorSystem("couchdb-actors")
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  Kamon.loadReportersFromConfig

  Couch.databases.take(5).map((id: String) => {
    val merchantActor = system.actorOf(Props(new DbProcessorActor(id)))
    merchantActor ! DbProcessorActor.Download
  })
}
