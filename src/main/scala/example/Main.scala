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

  val db = Database.forConfig("databaseUrl")
  try {

    // The query interface for the Suppliers table
    val suppliers: TableQuery[Suppliers] = TableQuery[Suppliers]

    // the query interface for the Coffees table
    val coffees: TableQuery[Coffees] = TableQuery[Coffees]

    val setupAction: DBIO[Unit] = DBIO.seq(
      // Create the schema by combining the DDLs for the Suppliers and Coffees
      // tables using the query interfaces
      (suppliers.schema ++ coffees.schema).create,
      // Insert some suppliers
      suppliers += (101, "Acme, Inc.", "99 Market Street", "Groundsville", "CA", "95199"),
      suppliers += (49, "Superior Coffee", "1 Party Place", "Mendocino", "CA", "95460"),
      suppliers += (150, "The High Ground", "100 Coffee Lane", "Meadows", "CA", "93966")
    )

    val setupFuture: Future[Unit] = db.run(setupAction)
    val f = setupFuture.flatMap { _ =>
      //#insertAction
      // Insert some coffees (using JDBC's batch insert feature)
      val insertAction: DBIO[Option[Int]] = coffees ++= Seq(
        ("Colombian", 101, 7.99, 0, 0),
        ("French_Roast", 49, 8.99, 0, 0),
        ("Espresso", 150, 9.99, 0, 0),
        ("Colombian_Decaf", 101, 8.99, 0, 0),
        ("French_Roast_Decaf", 49, 9.99, 0, 0)
      )

      val insertAndPrintAction: DBIO[Unit] = insertAction.map {
        coffeesInsertResult =>
          // Print the number of rows inserted
          coffeesInsertResult foreach { numRows =>
            println(s"Inserted $numRows rows into the Coffees table")
          }
      }

      val allSuppliersAction
          : DBIO[Seq[(Int, String, String, String, String, String)]] =
        suppliers.result

      val combinedAction
          : DBIO[Seq[(Int, String, String, String, String, String)]] =
        insertAndPrintAction andThen allSuppliersAction

      val combinedFuture
          : Future[Seq[(Int, String, String, String, String, String)]] =
        db.run(combinedAction)

      combinedFuture.map { allSuppliers =>
        allSuppliers.foreach(println)
      }

    }
    Await.result(f, Duration.Inf)
  } catch {
    case e: org.postgresql.util.PSQLException => {
      e.getMessage() match {
        case s
            if s.startsWith("ERROR: relation \"SUPPLIERS\" already exists") =>
          logger.info(s"good exception: ${s}")
        case other =>
          logger.info(s"bad exception: ${other}"); throw new Exception(e)
      }
    }
  }

  val query1: SQLActionBuilder = sql"select trunc(extract(epoch from now()))"
  val foo = query1.as[Int].head.map { x => 1 }

  val a = sql"select trunc(extract(epoch from now()))".as[Int].head.map { i => logger.info(s"here with : ${i}"); i }
  val dbFuture = db.run(a.transactionally)
  Await.result(dbFuture, 60 seconds)

}
