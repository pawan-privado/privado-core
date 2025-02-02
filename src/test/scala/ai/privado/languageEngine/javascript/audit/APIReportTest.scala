package ai.privado.languageEngine.javascript.audit

import ai.privado.audit.APIReport
import ai.privado.entrypoint.PrivadoInput
import ai.privado.languageEngine.javascript.audit.APIReportTestBase
import ai.privado.languageEngine.javascript.tagger.sink.{JSAPITagger, RegularSinkTagger}
import ai.privado.languageEngine.javascript.tagger.source.{IdentifierTagger, LiteralTagger}

import scala.collection.mutable
import scala.util.Try

class APIReportTest extends APIReportTestBase {
  override val javascriptFileContentMap: Map[String, String] = getContent()

  override def beforeAll(): Unit = {
    super.beforeAll()
    new IdentifierTagger(cpg, ruleCache, taggerCache).createAndApply()
    new JSAPITagger(cpg, ruleCache, PrivadoInput())
  }

  def getContent(): Map[String, String] = {
    val testJavaScriptFileMap = mutable.HashMap[String, String]()
    testJavaScriptFileMap.put(
      "main.js",
      """
        |async function main() {
        |    const randomVar = "ooo";
        |    const response = await client.post("http://www.example.com", randomVar);
        |}
        |""".stripMargin
    )

    testJavaScriptFileMap.toMap
  }

  "Test API sheet" should {
    "should return correct apis tagging" in {
      val workflowResult = APIReport.processAPIAudit(Try(cpg), ruleCache)

      val codeSet = mutable.HashSet[String]()
      val lineSet = mutable.HashSet[String]()
      val fileSet = mutable.HashSet[String]()

      workflowResult.foreach(row => {
        codeSet += row.head
        fileSet += row(1)
        lineSet += row(2)
      })

      workflowResult.size shouldBe 2
      codeSet should contain("client.post(\"http://www.example.com\", randomVar)")
      fileSet should contain("main.js")
      lineSet should contain("4")
    }
  }

}
