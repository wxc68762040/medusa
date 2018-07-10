package tutorial.webapp

import utest._

import org.scalajs.jquery.jQuery

object TutorialTest extends TestSuite {

  // Initialize App
  TutorialApp2.setupUI()

  def tests = TestSuite {
    'HelloWorld {
      assert(jQuery("p:contains('hello, world.')").length == 1)
    }

    'ButtonClick {
      def messageCount =
        jQuery("p:contains('you click me...')").length

      val button = jQuery("button:contains('clickMe.')")
      assert(button.length == 1)
      assert(messageCount == 0)

      for (c <- 1 to 5) {
        button.click()
        assert(messageCount == c)
      }
    }
  }
}