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
package ai.privado.languageEngine.go.tagger.sink

import ai.privado.cache.RuleCache
import ai.privado.entrypoint.PrivadoInput
import ai.privado.metric.MetricHandler
import ai.privado.tagger.sink.APITagger
import io.circe.Json
import io.shiftleft.codepropertygraph.generated.Cpg
import org.slf4j.LoggerFactory

class GoAPITagger(cpg: Cpg, ruleCache: RuleCache, privadoInput: PrivadoInput)
    extends APITagger(cpg, ruleCache, privadoInput) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  MetricHandler.metricsData("apiTaggerVersion") = Json.fromString("Common HTTP Libraries Used")

}
