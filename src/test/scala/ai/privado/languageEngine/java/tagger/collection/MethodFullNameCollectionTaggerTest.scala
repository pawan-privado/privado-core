package ai.privado.languageEngine.java.tagger.collection

import ai.privado.languageEngine.java.{AbstractTaggingSpec, TestCodeSnippet}
import ai.privado.model.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.semanticcpg.language.*

class MethodFullNameCollectionTaggerTest extends AbstractTaggingSpec {
  val collectionRule: RuleInfo = RuleInfo(
    "Collections.Spark.HttpFramework",
    "Spark Java Http Framework Endpoints",
    "",
    FilterProperty.METHOD_FULL_NAME,
    Array(),
    List(".*\\b(get|post|put)\\b.*"),
    false,
    "",
    Map(),
    NodeType.REGULAR,
    "",
    CatLevelOne.COLLECTIONS,
    catLevelTwo = Constants.default,
    Language.JAVA,
    Array()
  )

  "Spark Http Framework" should {
    "tag collection endpoint when handler is a method" in {
      var cpg: Cpg = null
      try {
        val javaFileContents: String =
          """
            |import static spark.*;
            |
            |public class HelloWorld {
            |    public static void main(String[] args) {
            |        Spark.get("/hello", (req, res) -> "Hello World");
            |    }
            |}""".stripMargin
        cpg = buildCpg(TestCodeSnippet(sourceCode = javaFileContents, language = Language.JAVA))
        cpg.call.methodFullName(".*get.*").l.size shouldBe 1
        cpg.call.head.code.contains("get") shouldBe true

        val collectionTagger = new MethodFullNameCollectionTagger(cpg, ruleCacheWithCollectionRule(collectionRule))
        collectionTagger.createAndApply()

        val ingressRules = collectionTagger.getIngressUrls()
        ingressRules should contain("\"/hello\"")

        val getCalls = cpg.call.methodFullName(".*get.*").l
        getCalls should have size 1
        getCalls.name.toSeq should contain theSameElementsAs List("get")

        val tags = getCalls.head.argument.isMethodRef.head.referencedMethod.tag.l
        tags should have size 6
        tags.nameExact(Constants.id).head.value shouldBe ("Collections.Spark.HttpFramework")
        tags.nameExact(Constants.catLevelOne).head.value shouldBe Constants.collections
        tags.nameExact(Constants.catLevelTwo).head.value shouldBe Constants.default
        tags.nameExact(Constants.nodeType).head.value shouldBe "REGULAR"
        tags.nameExact("COLLECTION_METHOD_ENDPOINT").head.value shouldBe "\"/hello\""
      } finally {
        if (cpg != null) {
          cpg.close()
        }
      }
    }
    "tag collection endpoint when handler is a method reference to a static method in same class" in {
      var cpg: Cpg = null
      try {
        val javaFileContents: String =
          """
            |import static spark.Spark.*;
            |import spark.Request;
            |import spark.Response;
            |
            |public class HelloWorld {
            |    public static String anotherHandler(Request req, Response res) {
            |        return "something";
            |    }
            |
            |    public static void main(String[] args) {
            |        put("/another", this::anotherHandler);
            |    }
            |}""".stripMargin
        cpg = buildCpg(TestCodeSnippet(sourceCode = javaFileContents, language = Language.JAVA))
        cpg.call.methodFullName(".*put.*").l.size shouldBe 1
        cpg.call.head.code.contains("put") shouldBe true

        val collectionTagger = new MethodFullNameCollectionTagger(cpg, ruleCacheWithCollectionRule(collectionRule))
        collectionTagger.createAndApply()

        val ingressRules = collectionTagger.getIngressUrls()
        ingressRules should contain("\"/another\"")

        val getCalls = cpg.call.methodFullName(".*put.*").l
        getCalls should have size 1
        getCalls.name.toSeq should contain theSameElementsAs List("put")

        val tags = cpg.method.fullName(".*anotherHandler.*").head.tag.l
        tags should have size 6
        tags.nameExact(Constants.id).head.value shouldBe ("Collections.Spark.HttpFramework")
        tags.nameExact(Constants.catLevelOne).head.value shouldBe Constants.collections
        tags.nameExact(Constants.catLevelTwo).head.value shouldBe Constants.default
        tags.nameExact(Constants.nodeType).head.value shouldBe "REGULAR"
        tags.nameExact("COLLECTION_METHOD_ENDPOINT").head.value shouldBe "\"/another\""
      } finally {
        if (cpg != null) {
          cpg.close()
        }
      }
    }
  }
}
