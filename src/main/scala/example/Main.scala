package example

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api._
import com.typesafe.scalalogging.Logger
import slick.jdbc.SQLActionBuilder

object Main extends App {

  val logger = Logger("example")

  val db = Database.forConfig("database")

  def setupDB() = {
    logger.info("For this example we don't need any DB setup")
  }

  def runConstantDBPings() = {
    val pingQuery =
      sql"select 1".as[Option[String]].map { 
        x => logger.info("Received ping") 
      }
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

  if (false) {
    // Query that takes 1 second to execute and returns the current timestamp (as of the beginning of the transaction)
    // Note: strange passing of id parameter is because slick provides no way other than string interpolation to
    // make a plain sql query.
    def workQuery(id: Int) =
      sql"select #$id, trunc(extract(epoch from now())) from (select pg_sleep(1)) as nothing"
        .as[(Int, Int)]
        .head

    val n =
      19 // specify concurrency -- if n < database.numThreads then both plain and nested queries will "work"
    //                        if n >= database.numThreads then the nested queries will starve the pool
    // If .transactionally is removed from query execution calls below, starvation is not seen until n
    // is much larger (19 in the case of the 10-core test machine). This failure mode is also slightl
    // different in that the database thread pool does not seem to itself starve -- the ping
    // queries keep on being sent. There is however no results receiving activity suggesting that
    // something starved that is needed to receive results.

    // Execute database transactions that take some time (1 second), log their results
    def loggingWorkQuery(id: Int) = workQuery(id).map { result =>
      logger.info(s"Work query ${result._1} returned: ${result._2}")
    }
    val workTasks = 1 to n map { i =>
      {
        logger.info(s"Executing work query ${i}")
        db.run(loggingWorkQuery(i).transactionally)
      }
    }
    // Wait for those to run
    Await.result(Future.sequence(workTasks), longTime)
    logger.info("Non-nested queries completed")

    // Execute database transactions that take some time (1 second), log their results
    // But also execute a second database transaction inside the result function
    def nestedWorkQuery(id: Int) = workQuery(id).map { result =>
      logger.info(s"Nested work query ${result._1} returned: ${result._2}")
    }
    def evilWorkQuery(id: Int) = workQuery(id).map { result =>
      {
        val nestedQueryId = id + n
        logger.info(
          s"Evil work query ${result._1} returned:  ${result._2}, executing nested work query ${nestedQueryId}"
        )
        val nestedFuture = db.run(nestedWorkQuery(nestedQueryId))
        // Wait for its result (needed to starve the connection pool)
        Await.result(nestedFuture, longTime)
      }
    }
    val evilWorkTasks = 1 to n map { i =>
      {
        logger.info(s"Executing evil work query ${i}")
        db.run(evilWorkQuery(i).transactionally)
      }
    }
    // Wait for those to run
    Await.result(Future.sequence(evilWorkTasks), longTime)
    // When the starvation syndrome occurs, we never get to here
    logger.info("Nested queries completed")
  }
  // Sleep for a while longer to allow us to see if the concurrent pings are succeeding
  Thread.sleep(100000)

}
