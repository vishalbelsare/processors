package org.clulab.struct

object GraphMap {
  type Type = Map[String, DirectedGraph[String]]

  val empty: Type = Map.empty

  val UNIVERSAL_BASIC = "universal-basic" // basic Universal dependencies
  val UNIVERSAL_ENHANCED = "universal-enhanced" // collapsed (or enhanced) Universal dependencies
  val STANFORD_BASIC = "stanford-basic" // basic Stanford dependencies
  val STANFORD_COLLAPSED = "stanford-collapsed" // collapsed Stanford dependencies
  val SEMANTIC_ROLES = "semantic-roles" // semantic roles from CoNLL 2008-09, which includes PropBank and NomBank
  val ENHANCED_SEMANTIC_ROLES = "enhanced-semantic-roles" // enhanced semantic roles
  val HYBRID_DEPENDENCIES = "hybrid" // graph that merges ENHANCED_SEMANTIC_ROLES and UNIVERSAL_ENHANCED
}
