/*
 * This file is part of Privado OSS.
 *
 * Privado is an open source static code analysis tool to discover data flows in the code.
 * Copyright (C) 2022 Privado, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, contact support@privado.ai
 *
 */

package ai.privado.languageEngine.java.passes.config

import ai.privado.cache.RuleCache
import ai.privado.languageEngine.java.language.*
import ai.privado.model.Language
import ai.privado.utility.PropertyParserPass
import better.files.File
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.X2Cpg.applyDefaultOverlays
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{AstNode, JavaProperty, Literal, Method, MethodParameterIn}
import io.shiftleft.semanticcpg.language.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import ai.privado.exporter.HttpConnectionMetadataExporter

class AnnotationTests extends PropertiesFilePassTestBase(".properties") {
  override val configFileContents: String =
    """
      |internal.logger.api.base=https://logger.privado.ai/
      |slack.base.url=https://hooks.slack.com/services/some/leaking/url
      |""".stripMargin

  override val propertyFileContents = ""
  override val codeFileContents: String =
    """
      |
      |import org.springframework.beans.factory.annotation.Value;
      |
      |class Foo {
      |
      |private static String loggerUrl;
      |
      |@Value("${slack.base.url}")
      |private static final String slackWebHookURL;
      |
      |public AuthenticationService(UserRepository userr, SessionsR sesr, ModelMapper mapper,
      |			ObjectMapper objectMapper, @Qualifier("ApiCaller") ExecutorService apiExecutor, SlackStub slackStub,
      |			SendGridStub sgStub, @Value("${internal.logger.api.base}") String loggerBaseURL) {
      |   }
      |
      |@Value("${internal.logger.api.base}")
      |public void setLoggerUrl( String pLoggerUrl )
      |{
      |        loggerUrl = pLoggerUrl;
      |}
      |}
      |""".stripMargin

  "ConfigFilePass" should {
    "connect annotated parameter to property" in {
      val anno: List[AstNode] = cpg.property.usedAt.l
      anno.length shouldBe 3

      anno.code.l shouldBe List(
        "@Value(\"${internal.logger.api.base}\") String loggerBaseURL",
        "java.lang.String loggerUrl",
        "java.lang.String slackWebHookURL"
      )
    }

    "connect property to annotated parameter" in {
      cpg.property.usedAt.originalProperty.l.length shouldBe 3
      cpg.property.usedAt.originalProperty.name.l shouldBe List(
        "internal.logger.api.base",
        "internal.logger.api.base",
        "slack.base.url"
      )
      cpg.property.usedAt.originalProperty.value.l shouldBe List(
        "https://logger.privado.ai/",
        "https://logger.privado.ai/",
        "https://hooks.slack.com/services/some/leaking/url"
      )
    }

    "connect the referenced member to the original property denoted by the annotated method" in {
      cpg.member("loggerUrl").originalProperty.size shouldBe 1
      cpg.member("loggerUrl").originalProperty.name.l shouldBe List("internal.logger.api.base")
      cpg.member("loggerUrl").originalProperty.value.l shouldBe List("https://logger.privado.ai/")
    }
  }
}

class JsonPropertyTests extends PropertiesFilePassTestBase(".json") {
  override val configFileContents = """
      |{
      |    "databases": [
      |      {
      |        "name": "MySQL Database",
      |        "uri": "mysql://username:password@hostname:port/database_name"
      |      }
      |     ],
      |     "mongoUri" : "mongodb://username:password@hostname:port/database_name"
      |}
      |""".stripMargin

  override val codeFileContents = ""

  override val propertyFileContents = ""

  "json file having array nodes" should {
    "get parsed and property nodes should be generated" in {

      new PropertyParserPass(cpg, inputDir.toString(), new RuleCache, Language.JAVASCRIPT).createAndApply()
      cpg.property.map(p => (p.name, p.value)).l shouldBe List(
        ("databases[0].name", "MySQL Database"),
        ("databases[0].uri", "mysql://username:password@hostname:port/database_name"),
        ("mongoUri", "mongodb://username:password@hostname:port/database_name")
      )

    }
  }
}
class GetPropertyTests extends PropertiesFilePassTestBase(".properties") {
  override val configFileContents = """
      |accounts.datasource.url=jdbc:mariadb://localhost:3306/accounts?useSSL=false
      |internal.logger.api.base=https://logger.privado.ai/
      |""".stripMargin
  override val codeFileContents =
    """
      | import org.springframework.core.env.Environment;
      |
      |public class GeneralConfig {
      |   public DataSource dataSource() {
      |     DriverManagerDataSource dataSource = new DriverManagerDataSource();
      |     dataSource.setUrl(env.getProperty("accounts.datasource.url"));
      |     return dataSource;
      |     }
      |}
      |""".stripMargin

  override val propertyFileContents = ""

  "ConfigFilePass" should {
    "create a file node for the property file" in {
      val List(_, _, name: String) = cpg.file.name.l // The default overlays add a new file to cpg.file
      name.endsWith("/test.properties") shouldBe true
    }

    "create a `property` node for each property" in {
      val properties = cpg.property.map(x => (x.name, x.value)).toMap
      properties
        .get("accounts.datasource.url")
        .contains("jdbc:mariadb://localhost:3306/accounts?useSSL=false") shouldBe true
      properties.get("internal.logger.api.base").contains("https://logger.privado.ai/")
    }

    "connect property nodes to file" in {
      val List(filename: String) = cpg.property.file.name.dedup.l
      filename.endsWith("/test.properties") shouldBe true
    }

    "connect property node to literal via `IS_USED_AT` edge" in {
      val List(lit: Literal) = cpg.property.usedAt.l
      lit.code shouldBe "\"accounts.datasource.url\""
    }
    "connect literal node to property via `ORIGINAL_PROPERTY` edge" in {
      val List(javaP: JavaProperty) = cpg.property.usedAt.originalProperty.l
      javaP.value shouldBe "jdbc:mariadb://localhost:3306/accounts?useSSL=false"

      val List(lit: Literal) = cpg.property.usedAt.l
      lit.originalProperty.head.value shouldBe "jdbc:mariadb://localhost:3306/accounts?useSSL=false"
      lit.originalPropertyValue.head shouldBe "jdbc:mariadb://localhost:3306/accounts?useSSL=false"
    }
  }
}

class EgressPropertyTests extends PropertiesFilePassTestBase(".yaml") {
  override val configFileContents = """
                                      |spring:
                                      |   application:
                                      |       name: basepath
                                      |mx-record-delete:
                                      |    events:
                                      |      - http:
                                      |          path: /v1/student/{id}
                                      |          method: DELETE
                                      |      - https:
                                      |          path: v1/student/{id}
                                      |          method: GET
                                      |      - ftp:
                                      |          path: student/{id}
                                      |          method: PUT
                                      |      - ssm:
                                      |          path: /
                                      |          method: PUT
                                      |""".stripMargin
  override val codeFileContents =
    """
      | import org.springframework.core.env.Environment;
      |""".stripMargin

  override val propertyFileContents = ""

  "Fetch egress urls from property files" ignore {
    "Check egress urls" in {
      val egressExporter   = HttpConnectionMetadataExporter(cpg, new RuleCache)
      val List(url1, url2) = egressExporter.getEgressUrls
      url1 shouldBe "/v1/student/{id}"
      url2 shouldBe "v1/student/{id}"
    }

    "Check egress urls with single char" in {
      val egressExporter       = HttpConnectionMetadataExporter(cpg, new RuleCache)
      val egressWithSingleChar = egressExporter.getEgressUrls.filter(x => x.size == 1)
      egressWithSingleChar.size shouldBe 0
    }
    "Check application base path" in {
      val httpConnectionMetadataExporter = HttpConnectionMetadataExporter(cpg, new RuleCache)
      val List(basePath)                 = httpConnectionMetadataExporter.getEndPointBasePath
      basePath shouldBe "basepath"
    }
  }
}

// Unit test to check if property is added in the cpg using XML files.
class XMLPropertyTests extends PropertiesFilePassTestBase(".xml") {
  override val configFileContents =
    """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
      |<beans>
      |<bean id="myField" class="com.example.test.GFG">
      |    <property name="staticField" value="${jdbc.url}"/>
      |    <property name="static_two" value="hello-world"/>
      |</bean>
      |<bean id="myField" class="com.example.test.MFM">
      |    <property name="testProperty" ref="myField"/>
      |</bean>
      |</beans>
      |""".stripMargin

  override val propertyFileContents: String =
    """jdbc.url=http://localhost:8081/""".stripMargin
  override val codeFileContents =
    """
      |package com.example.test;
      |
      |import java.util.*;
      |import java.io.*;
      |
      |public class GFG {
      |	private String staticField;
      |}
      |""".stripMargin

  "ConfigFilePass" should {
    "create a file node for the property file" in {
      val List(name: String) = cpg.file.name.l.filter(file => file.endsWith(".xml"))
      name.endsWith("/test.xml") shouldBe true
    }
  }

  "create a `property` node for each property" in {
    val properties = cpg.property.map(x => (x.name, x.value)).toMap
    properties
      .get("static_two")
      .contains("hello-world") shouldBe true
  }

  "Two way edge between member and propertyNode" in {
    val properties = cpg.property.usedAt.originalProperty.l.map(property => (property.name, property.value)).toMap;
    properties
      .get("staticField")
      .contains("http://localhost:8081/") shouldBe true
  }

  "Two way edge between member and propertyNode for no code reference" in {
    val properties = cpg.property.usedAt.originalProperty.l.map(property => (property.name, property.value)).toMap;
    properties
      .contains("static_two") shouldBe false
  }

  "References to another beans should be skipped" in {
    val properties = cpg.property.map(property => (property.name, property.value)).toMap;
    properties
      .contains("testProperty") shouldBe false
  }
}

/** Base class for tests on properties files and Java code.
  */
// file extension to support for any file as properties
abstract class PropertiesFilePassTestBase(fileExtension: String)
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  var cpg: Cpg = _
  val configFileContents: String
  val codeFileContents: String
  var inputDir: File   = _
  var outputFile: File = _
  val propertyFileContents: String

  override def beforeAll(): Unit = {
    inputDir = File.newTemporaryDirectory()
    (inputDir / s"test$fileExtension").write(configFileContents)

//    (inputDir / "unrelated.file").write("foo")
    if (propertyFileContents.nonEmpty) {
      (inputDir / "application.properties").write(propertyFileContents)
    }
    outputFile = File.newTemporaryFile()

    (inputDir / "GeneralConfig.java").write(codeFileContents)
    val config = Config().withInputPath(inputDir.pathAsString).withOutputPath(outputFile.pathAsString)

    cpg = new JavaSrc2Cpg()
      .createCpg(config)
      .map { cpg =>
        applyDefaultOverlays(cpg)
        cpg
      }
      .get
    new PropertyParserPass(cpg, inputDir.toString(), new RuleCache, Language.JAVA).createAndApply()
    new JavaPropertyLinkerPass(cpg).createAndApply()

    super.beforeAll()
  }

  override def afterAll(): Unit = {
    inputDir.delete()
    cpg.close()
    outputFile.delete()
    super.afterAll()
  }

}
