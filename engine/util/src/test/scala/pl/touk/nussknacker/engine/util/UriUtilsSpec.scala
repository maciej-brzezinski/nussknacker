package pl.touk.nussknacker.engine.util

import org.scalatest.{FunSuite, Matchers}

class UriUtilsSpec extends FunSuite with Matchers {

  test("encodeURIComponent") {
    val str = "Lorem Ipsum/is\\simply!dummy(text)of~the'printing."
    UriUtils.encodeURIComponent(str) shouldBe "Lorem%20Ipsum%2Fis%5Csimply%21dummy%28text%29of%7Ethe%27printing."
  }

}
