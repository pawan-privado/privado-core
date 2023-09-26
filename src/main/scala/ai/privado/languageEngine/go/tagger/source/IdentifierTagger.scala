package ai.privado.languageEngine.go.tagger.source

import ai.privado.cache.{RuleCache, TaggerCache}
import ai.privado.model.RuleInfo
import ai.privado.tagger.PrivadoParallelCpgPass
import ai.privado.model.{CatLevelOne, Constants, InternalTag, RuleInfo}
import ai.privado.utility.Utilities.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.semanticcpg.language.*

class IdentifierTagger(cpg: Cpg, ruleCache: RuleCache, taggerCache: TaggerCache)
    extends PrivadoParallelCpgPass[RuleInfo](cpg) {

  override def generateParts(): Array[RuleInfo] = ruleCache.getRule.sources.toArray

  override def runOnPart(builder: DiffGraphBuilder, ruleInfo: RuleInfo): Unit = {

    // Step 1.1
    val rulePattern = ruleInfo.combinedRulePattern
    val regexMatchingIdentifiers =
      cpg.identifier(rulePattern).filterNot(item => item.name.equals(item.name.toUpperCase))
    regexMatchingIdentifiers.foreach(identifier => {
      storeForTag(builder, identifier, ruleCache)(InternalTag.VARIABLE_REGEX_IDENTIFIER.toString)
      addRuleTags(builder, identifier, ruleInfo, ruleCache)
    })

    val regexMatchingMembers = cpg.member.name(rulePattern).l
    regexMatchingMembers.foreach(member => {
      storeForTag(builder, member, ruleCache)(InternalTag.VARIABLE_REGEX_MEMBER.toString)
      addRuleTags(builder, member, ruleInfo, ruleCache)
    })

    val regexMatchingFieldIdentifiersIdentifiers =
      cpg.fieldAccess
        .where(
          _.fieldIdentifier
            .canonicalName(rulePattern)
            .filterNot(item => item.canonicalName.equals(item.canonicalName.toUpperCase))
        )
        .isCall
        .l
    regexMatchingFieldIdentifiersIdentifiers.foreach(identifier => {
      storeForTag(builder, identifier, ruleCache)(InternalTag.VARIABLE_REGEX_IDENTIFIER.toString)
      addRuleTags(builder, identifier, ruleInfo, ruleCache)
    })

  }

}
