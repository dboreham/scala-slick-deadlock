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
    val pingQuery =
      sql"select 1".as[Option[String]].map { x => logger.info("Received ping") }
    val pingFuture = Future {
      while (true) {
        logger.info("Sending ping")
        db.run(pingQuery)
        Thread.sleep(1000)
      }
    }
  }

  val longTime = 300 seconds

  // Main begins here
  setupDB()

  // Spin up a task that runs a DB query every second and prints some log output
  // This shows us whether slick is alive and working
  runConstantDBPings()

  // Query that takes 1 second to execute and returns the current timestamp (as of the beginning of the transaction)
  val workQuery =
    sql"select trunc(extract(epoch from now())) from (select pg_sleep(1)) as nothing"
      .as[Int]
      .head

  val n = 20
  // Execute database transactions that take some time (1 second), log their results
  val loggingWorkQuery = workQuery.map { result => logger.info(s"Work query returned: ${result}")}
  val workTasks = 1 to n map { i =>
    {
      logger.info(s"Executing work query ${i}")
      db.run(loggingWorkQuery.transactionally)
    }
  }
  // Wait for those to run
  Await.result(Future.sequence(workTasks), longTime)

  // Execute database transactions that take some time (1 second), log their results
  // But also execute a second database transaction inside the result function
  val nestedWorkQuery =  workQuery.map { result => logger.info(s"Nested work query returned: ${result}")}
  val evilWorkQuery = workQuery.map { result => {
    logger.info(s"Work query returned: ${result}, executing nested work query")
    val nestedFuture = db.run(nestedWorkQuery.transactionally)
    // Wait for its result
    Await.result(nestedFuture, longTime)
  }}
  val evilWorkTasks = 1 to n map { i =>
    {
      logger.info(s"Executing evil work query ${i}")
      db.run(evilWorkQuery.transactionally)
    }
  }
  // Wait for those to run
  Await.result(Future.sequence(evilWorkTasks), longTime)

}
