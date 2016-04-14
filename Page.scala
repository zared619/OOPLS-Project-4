  
class Page(url: String, html: String) {
    val terms = html.split("(_|\\W)+").map(_.toLowerCase)
  
 
    def containsAll(searchTerms: Seq[String]) = {
        searchTerms.forall {terms.contains(_)}
    }
  
  	def getLinks : List[String] = {
        val baseURL = url
		// See http://www.mkyong.com/regular-expressions/how-to-extract-html-links-with-regular-expression/ for explanation of regex
		val aTagRegex = """(?i)<a([^>]+)>(.+?)</a>""".r
		val urlRegex = """\s*(?i)href\s*=\s*(\"([^"]*\")|'[^']*'|([^'">\s]+))""".r
		
		val opts = for ( a <- aTagRegex findAllMatchIn html ) yield urlRegex.findFirstMatchIn(a.toString)
		
		val hrefs = opts collect { case Some(x) => x group 1 }
		
		// remove leading and trailing quotes, if any
		val cleaned = hrefs map { _.stripPrefix("\"").stripPrefix("\'").stripSuffix("\"").stripPrefix("\'") } filter { ! _.startsWith("javascript") }
		
		// Use Java's URL class to parse the URL
		//   and get the full URL string (including implied context)
		val contextURL = new java.net.URL(baseURL)
		
        def getURL(x: String) = {
          var result = ""
          try {
            result = new java.net.URL(contextURL, x).toString()
          }
          catch {
            case e: java.net.MalformedURLException => Unit
          }
          result
        }
        
		(cleaned map { getURL(_) } ).filter(_.length > 0).toList
	}
}