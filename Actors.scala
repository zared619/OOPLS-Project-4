import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef

// Diagram of Actors and messages
//
//                                       |
//                                       |
//                                 StartIndexing
//                                       |
//                                       V

/////////////  <-- IndexRequest --   ////////////  <----- Query ----- //////////////
// Fetcher //                        // Master //                     // Prompter //
///////////// -- Option[RawPage] --> ////////////  -- QueryResult --> //////////////


//////////////////////////////////
// Case classes for messages

case class StartIndexing(urls: Seq[String])

case class IndexRequest(url: String)
case class RawPage(url: String, html: String) {
  // Override toString so printed messages do not
  //   dump the full html
  override def toString() = url
}

case class Query(terms: Seq[String])
case class QueryResult(fractionContaining: Double, numTotalPages: Int)

//////////////////////////////////
// Actors
 
class Fetcher extends Actor {
	def receive = {
		case IndexRequest(url: String) => {
			try{
				val src = scala.io.Source.fromURL( url ).getLines.mkString("\n")
				sender ! Some(RawPage(url, src))
			}
			catch {
				case e: java.io.IOException => sender ! None
			}
		}
	}
}

// Prompter asks the user to enter queries,
//   then sends them along to the Master,
//   receiving and displaying results
class Prompter extends Actor {
 
  // To add logging of messages received,
  //   1. edit the application.conf file
  //   2. use the following line instead of def receive = {
  def receive = akka.event.LoggingReceive {
  
  //def receive = {
    case QueryResult(fracContaining, numTotalPages) => {
      if(numTotalPages > 0){
        println((fracContaining*100.0) + "% of " + numTotalPages + " total pages matched.")
      }
      
      // Prompt for the next query
      val q = scala.io.StdIn.readLine("Enter a query: ")
      
      sender ! Query(if(q.length == 0) Nil else q.split("(_|\\W)+").map(_.toLowerCase))
    }
  }
}

class Master extends Actor {
  // TODO: Set maxPages lower for initial testing
  val maxPages = 1000

  val urlsToIndex = scala.collection.mutable.HashSet[String]()
  
  // indexedUrls duplicates information with indexedPages,
  //   but indexedUrls is quick to check for duplicates
  val indexedPages = scala.collection.mutable.ListBuffer[Page]()
  val indexedUrls = scala.collection.mutable.HashSet[String]()
  
	private def createWorkers(numActors: Int) = {		
		for (i <- 0 until numActors) yield context.actorOf(Props[Fetcher], name = s"worker-${i}")
	}
	
	private def beginWorking(fileNames: Seq[String], workers: Seq[ActorRef]) = {
		fileNames.zipWithIndex.foreach(thing => { workers(thing._2) ! IndexRequest(thing._1) } )
	}
  
  def receive = {
   
    // TODO: handle StartIndexing message
	case StartIndexing(urls: Seq[String]) => {
		val workers = createWorkers(urls.length)
		beginWorking(urls, workers)
		
		context.actorOf(Props[Prompter], name =s"THE_Prompter") ! QueryResult(0.0, 0)
	}
    
    // TODO: handle Query message
   case Query(terms: Seq[String]) => {
		if(terms.length == 0) {
			context.system.shutdown()
		}
		else {
			val frac: Double = indexedPages.count(_.containsAll(terms)).toDouble / indexedPages.length.toDouble
			sender ! QueryResult(frac,indexedPages.length)
		}
   }
   
    case x: Option[_] => {
      // See if we got a RawPage back
      x match {
        case Some(RawPage(url, html)) => {
          // Add the page to the indexed collections
          val pg = new Page(url, html)
          indexedPages += pg
          indexedUrls += url
      
          // Add the links found in the page to the set of links to index
          urlsToIndex ++= pg.getLinks.filter( link =>
              !urlsToIndex.contains(link) && !indexedUrls.contains(link) )
        }
        case _ => Unit
      }  
      
      // Regardless of whether or not we got a RawPage,
      //   we should send another request to the Fetcher
      if (!urlsToIndex.isEmpty && indexedUrls.size < maxPages) {
        val nextUrl = urlsToIndex.head
        urlsToIndex.remove(nextUrl)
        sender ! IndexRequest(nextUrl)
      }
      // If urlsToIndex is empty, we should do something to prevent
      //   the sender Fetcher from remaining idle,
      //   but that is beyond our scope
    }
        
  }

}




    