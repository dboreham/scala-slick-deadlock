package example

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api._
import com.typesafe.scalalogging.Logger
import slick.jdbc.SQLActionBuilder

object keepForLater {

  def keepForLater(): Unit = {

    def f0(x: String): Future[Unit] = {
      System.err.print("f0:");
      f1(x)
    }

    def f1(x: String): Future[Unit] =
      Future {
        throw new RuntimeException("Blam!");
      }
    val f = f0("222")
    try {
      val r = Await.result(f, 10 seconds)
    } catch {
      // will print with f0 when agent is enabled
      case ex: Throwable => ex.printStackTrace
    }

  }
}

object Main extends App {

  val logger = Logger("example")

  val db = Database.forConfig("database")

  def setupDB() = {
    logger.info("For this example we don't need any DB setup")
  }

  def runConstantDBPings() = {
    val pingQuery = sql"select 1".as[Option[String]].map { x => logger.info("Received ping") }
    val pingFuture = Future {
      while(true) {
        logger.info("Sending ping")
        db.run(pingQuery)
        Thread.sleep(1000)
      }
    }
  }
  
  // Main begins here
  setupDB()

  // Spin up a task that runs a DB query every second and prints some log output
  // This shows us whether slick is alive and working
  runConstantDBPings()

  val query1: SQLActionBuilder = sql"select trunc(extract(epoch from now()))"
  val bar = query1.as[Int]
  val barFirst = bar.head
  val foo = query1.as[Int].head.map { x => x }

    val evilDelay =
    sql"select trunc(extract(epoch from now())) from (select pg_sleep(1)) as nothing"
      .as[Int]
      .head
      .map { i => logger.info(s"evil here with : ${i}"); i }

  def callbackFn(i: Int): Unit = {
    logger.info(s"here with : ${i}")
    // Bwahahaha... let's start a new DB transaction here
    val evilFuture = db.run(evilDelay.transactionally)
    Await.result(evilFuture, 60 seconds)
    logger.info(s"back here with : ${evilFuture.value}")
    ()
  }

  val delay =
    sql"select trunc(extract(epoch from now())) from (select pg_sleep(1)) as nothing"
      .as[Int]
      .head
      .map { i => callbackFn(i); i }

  val doublet = DBIO.sequence(Vector(barFirst, delay, delay, barFirst))
  val tasks = 1 to 2 map { i =>
    {
      db.run(doublet.transactionally)
    }
  }

  val megaFuture = Future.sequence(tasks)

  // val dbFuture = db.run(doublet.transactionally)
  Await.result(megaFuture, 60 seconds)
  logger.info(s"Got: ${megaFuture.value}")

}
