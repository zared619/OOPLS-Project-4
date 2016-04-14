import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.ActorSystem
import java.io._
import scala.io._

// scala -J-Xmx2g

object Main {
	def main(args: Array[String]): Unit = {
		val system = ActorSystem("indexing-system")
		
		val m = system.actorOf(Props[Master],name="master")
		
		m ! StartIndexing(args)
	}
}
