package org.clulab.odin.impl

import org.apache.commons.io.FileUtils.readFileToString
import org.apache.commons.text.StrSubstitutor
import org.clulab.odin._
import org.clulab.odin.impl.MarkdownGeneration._
import org.clulab.utils.FileUtils
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.{Constructor, ConstructorException}

import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.{Collection, Map => JMap}
import scala.collection.compat._
import scala.io.{Codec, Source}
import scala.jdk.CollectionConverters._
import scala.util.Using

class RuleReader(val actions: Actions, val charset: Charset, val ruleDir: Option[File] = None) {

  import RuleReader._

  // codec to be used by scala.io.Source
  implicit val codec: Codec = new Codec(charset)

  // invokes actions through reflection
  private val mirror = new ActionMirror(actions)

  val ruleYamlOpt =
      Option.when(OdinConfig.keepRule)(new Yaml(new Constructor(classOf[Map[String, Any]])))

  def read(input: String): Vector[Extractor] = {
    val rules = getRules(input)
    mkExtractors(rules)
  }

  def getRules(input: String): Seq[Rule] = {
    try {
      rulesFromMasterFile(input)
    } catch {
      case e: ConstructorException => rulesFromSimpleFile(input)
    }
  }

  protected def rulesFromSimpleFile(input: String): Seq[Rule] = {
    val yaml = new Yaml(new Constructor(classOf[Collection[JMap[String, Any]]]))
    val jRules = yaml.load(input).asInstanceOf[Collection[JMap[String, Any]]]
    // no resources are specified
    val resources = OdinResourceManager(Map.empty)
    val config = OdinConfig(resources = resources)
    readRules(jRules, config)
  }

  protected def rulesFromMasterFile(input: String): Seq[Rule] = {
    val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
    val master = yaml.load(input).asInstanceOf[JMap[String, Any]].asScala.toMap
    val taxonomy = master.get("taxonomy").map(readTaxonomy)
    val vars = getVars(master)
    val resources = readResources(master)
    val jRules = master("rules").asInstanceOf[Collection[JMap[String, Any]]]
    val graph = getGraph(master)
    val config = OdinConfig(taxonomy = taxonomy, resources = resources, variables = vars, graph = graph)
    readRules(jRules, config)
  }

  def getGraph(
    data: Map[String, Any],
    default: String = OdinConfig.DEFAULT_GRAPH
  ): String = data.get("graph") match {
    case None => default
    case Some(g) =>
      val graph = g.asInstanceOf[String]
      // graph must be of the registered types
      if (! OdinConfig.VALID_GRAPHS.contains(graph))
        throw OdinException(s"'$graph' is not a valid graph type. Valid options: ${
          OdinConfig.VALID_GRAPHS.map(s => s"'$s'").mkString(", ")
        }")
      graph
  }

  // Variables can be a string, or optionally a list of strings which are combined with OR.
  // This is largely to support clean diffs when changes are made to variables, e.g., triggers.
  private def processVar(varValue: Any): String = {
    varValue match {
      // If the variable is a string, clean the whitespace and return
      case s: String => cleanVar(s)
      // Else, if it's a list:
      case arr:java.util.ArrayList[_] => arr.asScala
        .map(_.toString.trim)
        .map(cleanVar)  // clean each
        .mkString("|")  // concatenate with OR
      case _ => ???
    }

  }

  // StrSubstitutor doesn't support whitespace in var (ex. ${ varName } )
  private def cleanVar(s: String): String = {
    val clean = s.
      replaceAll("\\$\\{\\s+", "\\$\\{").
      replaceAll("\\s+\\}", "\\}")
    clean
  }

  def mkExtractors(rules: Seq[Rule]): Vector[Extractor] = {
    // count names occurrences
    val names = rules groupBy (_.name) transform ((k, v) => v.size)
    // names should be unique
    names find (_._2 > 1) match {
      case None => (rules map mkExtractor).toVector // return extractors
      case Some((name, count)) =>
        throw new OdinNamedCompileException(s"rule name '$name' is not unique", name)
    }
  }

  def mkRule(
      data: Map[String, Any],
      expand: String => Seq[String],
      template: Any => String,
      config: OdinConfig
  ): Rule = {

    // name is required
    val name = try {
      template(data("name"))
    } catch {
      case e: NoSuchElementException => throw new OdinCompileException("unnamed rule")
    }

    // replace variables or throw an OdinNamedCompileException
    def tmpl(raw: Any): String = {
      try {
        template(raw)
      } catch {
        case e: NoSuchElementException =>
          val varName = e.getMessage.drop(15)
          throw new OdinNamedCompileException(s"var '$varName' not found in rule '$name'", name)
      }
    }

    // one or more labels are required
    val labels: Seq[String] = try {
      data("label") match {
        case label: String => expand(tmpl(label))
        case jLabels: Collection[_] =>
          jLabels.asScala.flatMap(l => expand(tmpl(l))).toSeq.distinct
      }
    } catch {
      case e: NoSuchElementException =>
        throw new OdinNamedCompileException(s"rule '$name' has no labels", name)
    }

    // pattern is required
    val pattern = try {
      tmpl(data("pattern"))
    } catch {
      case e: NoSuchElementException =>
        throw new OdinNamedCompileException(s"rule '$name' has no pattern", name)
    }

    // these fields have default values
    val ruleType = tmpl(data.getOrElse("type", DefaultType))
    val priority = tmpl(data.getOrElse("priority", DefaultPriority))
    val action = tmpl(data.getOrElse("action", DefaultAction))
    val keep = strToBool(tmpl(data.getOrElse("keep", DefaultKeep)))
    // unit is relevant to TokenPattern only
    val unit = tmpl(data.getOrElse("unit", DefaultUnit))
    // the graph to match against
    val graph: String = getGraph(data, config.graph)
    val updatedConfig = config.copy(graph = graph)
    val textOpt = ruleYamlOpt.map(_.dump(data.asJava))

    // make intermediary rule
    ruleType match {
      case DefaultType =>
        new Rule(name, labels, ruleType, unit, priority, keep, action, pattern, updatedConfig, textOpt)
      // ignore specification of 'graph' when using "type: dependency"
      case "dependency" =>
        new Rule(name, labels, ruleType, unit, priority, keep, action, pattern, updatedConfig.copy(graph = OdinConfig.DEFAULT_GRAPH), textOpt)
      case "token" =>
        new Rule(name, labels, ruleType, unit, priority, keep, action, pattern, updatedConfig, textOpt)
      // cross-sentence cases
      case "cross-sentence" =>
        (data.getOrElse("left-window", None), data.getOrElse("right-window", None)) match {
          case (leftWindow: Int, rightWindow: Int) =>
            new CrossSentenceRule(name, labels, ruleType, leftWindow, rightWindow, unit, priority, keep, action, pattern, updatedConfig, textOpt)
          case (leftWindow: Int, None) =>
            new CrossSentenceRule(name, labels, ruleType, leftWindow, DefaultWindow, unit, priority, keep, action, pattern, updatedConfig, textOpt)
          case (None, rightWindow: Int) =>
            new CrossSentenceRule(name, labels, ruleType, DefaultWindow, rightWindow, unit, priority, keep, action, pattern, updatedConfig, textOpt)
          case _ =>
            throw OdinCompileException(s""""cross-sentence" rule '$name' requires a "left-window" and/or "right-window"""")
        }
      // unrecognized rule type
      case other =>
        throw OdinCompileException(s"""type '$other' not recognized for rule '$name'""")
    }
  }

  protected def readRules(
      rules: Collection[JMap[String, Any]],
      config: OdinConfig
  ): Seq[Rule] = {

    // return Rule objects
    rules.asScala.toSeq.flatMap { r =>
      val m = r.asScala.toMap
      if (m.contains("import")) {
        // import rules from a file and return them
        importRules(m, config)
      } else {
        // gets a label and returns it and all its hypernyms
        val expand: String => Seq[String] = label => config.taxonomy match {
          case Some(t) => t.hypernymsFor(label)
          case None => Seq(label)
        }
        // interpolates a template variable with ${variableName} notation
        // note that $variableName is not supported and $ can't be escaped
        val template: Any => String = a => replaceVars(a.toString, config.variables)
        // return the rule (in a Seq because this is a flatMap)
        Seq(mkRule(m, expand, template, config))
      }
    }

  }

  /** gets the master file as a Map and returns the declared variables, if any */
  def getVars(data: Map[String, Any]): Map[String, String] = {
    data.get("vars").map(readOrImportVars).getOrElse(Map.empty)
  }

  /** Reads the variables declared directly or imports them from a file */
  def readOrImportVars(data: Any): Map[String, String] = data match {
    case vars: JMap[_, _] => vars.asScala.map{ case (k,v) => k.toString -> processVar(v) }.toMap
    case path: String =>
      val input = readFileOrResource(path)
      val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
      val vars = yaml.load(input).asInstanceOf[JMap[String, Any]]
      vars.asScala.map { case (k, v) => k -> processVar(v) }.toMap
  }

  /**
    * Tries to read the path as a file first. If it fails, then it tries
    * to read from the jar resources.
    *
    * @param s
    * @return
    */
  def readFileOrResource(s: String): String = {
    ruleDir match {
      case Some(path) =>
        val filepath = if (s.startsWith("/")) s.drop(1) else s
        val f = new File(path, filepath)
        readFileToString(f, StandardCharsets.UTF_8)
      case None =>
        val url = mkURL(s)
        Using.resource(Source.fromURL(url)) { source =>
          val data = source.mkString
          data
        }
    }
  }

  // reads a taxonomy from data, where data may be either a forest or a file path
  private def readTaxonomy(data: Any): Taxonomy = data match {
    case t: Collection[_] => Taxonomy(t.asInstanceOf[Collection[Any]])
    case path: String =>
      val input = readFileOrResource(path)
      val yaml = new Yaml(new Constructor(classOf[Collection[Any]]))
      val data = yaml.load(input).asInstanceOf[Collection[Any]]
      Taxonomy(data)
  }

  // reads resources from data, and constructs an OdinResourceManager instance
  private def readResources(data: Map[String, Any]): OdinResourceManager = {
    //println(s"resources: ${data.get("resources")}")
    val resourcesMap: Map[String, String] = data.get("resources") match {
      case Some(m: JMap[_, _]) => m.asScala.map(pair => (pair._1.toString, pair._2.toString)).toMap
      case _ => Map.empty
    }
    OdinResourceManager(resourcesMap)
  }


  private def importRules(
      data: Map[String, Any],
      config: OdinConfig
  ): Seq[Rule] = {
    // apply variable substitutions to import
    val path = {
      val p = data("import").toString
      val res = replaceVars(p, config.variables)
      res
    }
    val input = readFileOrResource(path)
    // read rules and vars from file
    val (jRules: Collection[JMap[String, Any]], localVars: Map[String, String]) = try {
      // try to read file with rules and optional vars by trying to read a JMap
      val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
      val data = yaml.load(input).asInstanceOf[JMap[String, Any]].asScala.toMap
      // read list of rules
      val jRules = data("rules").asInstanceOf[Collection[JMap[String, Any]]]
      // read optional vars
      val localVars = getVars(data)
      (jRules, localVars)
    } catch {
      case e: ConstructorException =>
        // try to read file with a list of rules by trying to read a Collection of JMaps
        val yaml = new Yaml(new Constructor(classOf[Collection[JMap[String, Any]]]))
        val jRules = yaml.load(input).asInstanceOf[Collection[JMap[String, Any]]]
        (jRules, Map.empty[String, String])
    }
    // variables specified by the call to `import`
    val importVars = getVars(data)
    // variable scope:
    // - an imported file may define its own variables (`localVars`)
    // - the importer file can define variables (`importerVars`) that override `localVars`
    // - a call to `import` can include variables (`importVars`) that override `importerVars`
    val updatedVars = localVars ++ config.variables ++ importVars
    val newConf = config.copy(variables = updatedVars)
    readRules(jRules, newConf)
  }

  private def replaceVars(s: String, vars: Map[String, String]): String = {
    val valuesMap = vars.asJava
    // NOTE: StrSubstitutor is NOT threadsafe
    val sub = new StrSubstitutor(valuesMap)
    // allow for recursive substitution
    sub.setEnableSubstitutionInVariables(true)
    val clean = cleanVar(s)
    sub.replace(clean)
  }

  // compiles a rule into an extractor
  private def mkExtractor(rule: Rule): Extractor = {
    try {
      rule.ruleType match {
        case "token" => mkTokenExtractor(rule)
        case DefaultType => mkGraphExtractor(rule)
        // FIXME: should this be deprecated?
        case "dependency" => mkGraphExtractor(rule)
        case "cross-sentence" => mkCrossSentenceExtractor(rule)
        case _ =>
          val msg = s"rule '${rule.name}' has unsupported type '${rule.ruleType}'"
          throw new OdinNamedCompileException(msg, rule.name)
      }
    } catch {
      case e: Exception =>
        val msg = s"Error parsing rule '${rule.name}': ${e.getMessage}"
        throw new OdinNamedCompileException(msg, rule.name)
    }
  }

  protected def newTokenExtractor(name: String, labels: Seq[String], priority: Priority, keep: Boolean, action: Action,
      pattern: TokenPattern, ruleOpt: Option[String]) = {
    new TokenExtractor(name, labels, priority, keep, action, pattern, ruleOpt)
  }

  protected def newCrossSentenceExtractor(name: String, labels: Seq[String], priority: Priority, keep: Boolean,
      action: Action, leftWindow: Int, rightWindow: Int, anchorPattern: TokenExtractor, neighborPattern: TokenExtractor,
      anchorRole: String, neighborRole: String, ruleOpt: Option[String]): CrossSentenceExtractor = {
    new CrossSentenceExtractor(name, labels, priority, keep, action, leftWindow, rightWindow,
        anchorPattern, neighborPattern, anchorRole, neighborRole, ruleOpt)
  }

  protected def newGraphExtractor(name: String, labels: Seq[String], priority: Priority, keep: Boolean, action: Action,
      pattern: GraphPattern, config: OdinConfig, ruleOpt: Option[String]): GraphExtractor = {
    new GraphExtractor(name, labels, priority, keep, action, pattern, config, ruleOpt)
  }

  // compiles a token extractor
  private def mkTokenExtractor(rule: Rule): TokenExtractor = {
    val name = rule.name
    val labels = rule.labels
    val priority = Priority(rule.priority)
    val keep = rule.keep
    val action = mirror.reflect(rule.action)
    val compiler = new TokenPatternParsers(rule.unit, rule.config)
    val pattern = compiler.compileTokenPattern(rule.pattern)
    newTokenExtractor(name, labels, priority, keep, action, pattern, rule.textOpt)
  }

  private def mkRuleText(map: JMap[String, Any]): String = {
    val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
    val ruleText = yaml.dump(map)

    ruleText
  }

  private def mkCrossSentenceExtractor(rule: Rule): CrossSentenceExtractor = {

    val lw: Int = rule match {
      case csr: CrossSentenceRule => csr.leftWindow
      case other => DefaultWindow
    }

    val rw: Int = rule match {
      case csr: CrossSentenceRule => csr.rightWindow
      case other => DefaultWindow
    }

    // convert cross-sentence pattern...
    // pattern: |
    //   argName1:ArgType = tokenpattern
    //   argName2: ArgType = tokenpattern
    // ...to Seq[(role, Rule)]
    val rolesWithRules: Seq[(String, Rule)] = for {
      // The toIndexedSeq is on behalf of higher versions of Scala.
      (argPattern, i) <- rule.pattern.split("\n").toIndexedSeq.zipWithIndex
      // ignore empty lines
      if argPattern.trim.nonEmpty
      // ignore comments
      if ! argPattern.trim.startsWith("#")
    } yield {
      // split arg pattern into 'argName1:ArgType', 'tokenpattern'
      // apply split only once
      val contents: Seq[String] = argPattern.split("\\s*=\\s*", 2).toIndexedSeq
      if (contents.size != 2) throw OdinException(s"'$argPattern' for rule '${rule.name}' must have the form 'argName:ArgType = tokenpattern'")
      // split 'argName1:ArgType' into 'argName1', 'ArgType'
      // apply split only once
      val argNameContents = contents.head.split("\\s*:\\s*", 2)
      if (argNameContents.size != 2) throw OdinException(s"'${contents.head}' for rule '${rule.name}' must have the form 'argName:ArgType'")
      val role = argNameContents.head.trim
      val label = argNameContents.last.trim
      // pattern for argument
      val pattern = contents.last.trim
      // make rule name
      val ruleName = s"${rule.name}_arg:$role"
      // labels from label
      val labels = rule.taxonomy match {
        case Some(t) => t.hypernymsFor(label)
        case None => Seq(label)
      }
      //println(s"labels for '$ruleName' with pattern '$pattern': '$labels'")
      // Do not apply cross-sentence rule's action to anchor and neighbor
      // This does not need to be stored
      val ruleTextOpt = rule.textOpt.map { text =>
        val map: JMap[String, Any] = Map(
          "name" -> ruleName,
          "label" -> labels.asJava,
          "type" -> "token",
          "unit" -> rule.unit,
          "priority" -> rule.priority,
          "keep" -> false,
          "pattern" -> pattern
        ).asJava

        mkRuleText(map)
      }

      (role, new Rule(ruleName, labels, "token", rule.unit, rule.priority, false, DefaultAction, pattern, rule.config, ruleTextOpt))
    }

    if (rolesWithRules.size != 2) throw OdinException(s"Pattern for '${rule.name}' must contain exactly two args")

    newCrossSentenceExtractor(
      name = rule.name,
      labels = rule.labels,
      priority = Priority(rule.priority),
      keep = rule.keep,
      action = mirror.reflect(rule.action),
      // the maximum number of sentences to look behind for pattern2
      leftWindow = lw,
      // the maximum number of sentences to look ahead for pattern2
      rightWindow = rw,
      anchorPattern = mkTokenExtractor(rolesWithRules.head._2),
      neighborPattern = mkTokenExtractor(rolesWithRules.last._2),
      anchorRole = rolesWithRules.head._1,
      neighborRole = rolesWithRules.last._1,
      rule.textOpt
    )
  }

  // compiles a dependency extractor
  private def mkGraphExtractor(rule: Rule): GraphExtractor = {
    val name = rule.name
    val labels = rule.labels
    val priority = Priority(rule.priority)
    val keep = rule.keep
    val action = mirror.reflect(rule.action)
    val compiler = new GraphPatternCompiler(rule.unit, rule.config)
    val pattern = compiler.compileGraphPattern(rule.pattern)
    newGraphExtractor(name, labels, priority, keep, action, pattern, rule.config, rule.textOpt)
  }

  /**
    * Autogenerate markdown file string content that shows the metadata for each rule in
    * the grammar.
    * This is useful for those who have a harder time sifting through the yaml files which
    * may be quite nested.  One md block per rule, sorted alphabetically.
    * @param input yaml rule string
    * @return String contents of the rule documentation file
    */
  def ruleSchemas(input: String): String = {
    val schemas = ruleSchemaObjects(input)
    // File header and contents
    (List("# Odin Rule Schemas\n") ++ schemas.map(_.toMarkdown)).mkString("\n")
  }

  /**
    * Autogenerate objects that can represent themselves as markdown blocks that show
    * the metadata for each rule in the grammar using a Master file.
    * This entrypoint is for people who want to edit what is produced prior to exporting.
    * One md block per rule, sorted alphabetically.
    * @param input yaml rule string
    * @return scala Map with markdown table contents
    */
  def ruleSchemaObjects(input: String): Seq[RuleSchema] = {
    val rules = getRules(input)
    val extractors = mkExtractors(rules)
    // To get all the information, we need both the rules and the extractors
    rules
      // pair each rule with its corresponding extractor
      .zip(extractors)
      // put them in alphabetical order by type and rule name
      .sortBy(e => (e._1.labels.head, e._1.name))
      // convert each to markdown representation
      .map(Function.tupled(toRuleSchema))
  }

  /**
    * Autogenerate markdown **file** that shows the metadata for each rule in the grammar.
    * This is useful for those who have a harder time sifting through the yaml files which
    * may be quite nested.  One md block per rule, sorted alphabetically.
    * @param input odin grammar yaml string
    * @param outname the path to the output md file
    */
  def exportRuleSchemas(input: String, outname: String): Unit = {
    val markdown = ruleSchemas(input)
    // export
    Using(FileUtils.printWriterFromFile(new File(outname))) { pw =>
      pw.println(markdown)
    }
  }

  /**
    * Autogenerate aggregated markdown documentation string for a grammar, aggregating by _Type_ (i.e.,
    * the Label from the internal Taxonomy).  This view of the grammar tells consumers what
    * they can expect to see (or what they _could_ expect to see) in an Extraction/Mention of
    * a given Type (or Label).
    * @param input the String content of the grammar yaml file
    * @return markdown file contents
    */
  def extractionSchemas(input: String, minimal: Boolean = false): String = {
    val schemas = extractionSchemaObjects(input)
    (List(s"# Odin Extraction Schemas\n") ++ schemas.map(_.toMarkdown(minimal))).mkString("\n")
  }

  /**
    * Autogenerate extractionSchema objects for a grammar, aggregating by _Type_ (i.e.,
    * the Label from the internal Taxonomy).
    * This entrypoint is for people who want to edit what is produced prior to exporting.
    * @param input the String content of the simple yaml file
    */
  def extractionSchemaObjects(input: String): Seq[ExtractionSchema] = {
    val rules = getRules(input)
    val extractors = mkExtractors(rules)

    // the order in which to present the schemas
    val order = extractors
      .map(e => (e.labels.reverse.mkString(","), e.label))
      .distinct
      .sortBy(_._1)
      .map(_._2)

    // aggregate the rules by the type of Mention produced (ref: the taxonomy)
    val byLabel = rules.zip(extractors)
      .groupBy(_._2.label)

    for {
      label <- order
      rulesAndExtractorsForLabel = byLabel(label)
    } yield toExtractionSchema(label, rulesAndExtractorsForLabel)

  }

  /**
    * Autogenerate aggregated markdown documentation **file** for a grammar, aggregating by _Type_ (i.e.,
    * the Label from the internal Taxonomy).  This view of the grammar tells consumers what
    * they can expect to see (or what they _could_ expect to see) in an Extraction/Mention of
    * a given Type (or Label).
    * @param input the String content of the grammar yaml file
    * @param outname output markdown file
    */
  def exportExtractionSchemas(input: String, outname: String, minimal: Boolean = false): Unit = {
    val markdown = extractionSchemas(input, minimal)
    Using.resource(FileUtils.printWriterFromFile(new File(outname))) { pw =>
      pw.println(markdown)
    }
  }


}

object RuleReader {
  val DefaultType = "graph"
  val DefaultPriority = "1+"
  val DefaultWindow = 0 // 0 means don't use a window
  val DefaultKeep = "true"
  val DefaultAction = "default"
  val DefaultUnit = "word"

  // interprets yaml boolean literals
  def strToBool(s: String): Boolean = s match {
    case "y" | "Y" | "yes" | "Yes" | "YES" | "true" | "True" | "TRUE" | "on" | "On" | "ON" => true
    case "n" | "N" | "no" | "No" | "NO" | "false" | "False" | "FALSE" | "off" | "Off" | "OFF" => false
    case b => sys.error(s"invalid boolean literal '$b'")
  }

  val CLASSPATH_PROTOCOL = "classpath:"

  /**
    * Makes a url from an odin import that considers the custom classpath protocol
    * @param path: String representing the URL
    * @return URL
    */
  def mkURL(path: String): URL = {
    // check if path is relative to resources
    val resource = getClass.getClassLoader.getResource(path)
    path match {
      case hasResource if resource != null => resource
      case cp if cp.startsWith(CLASSPATH_PROTOCOL) =>
        val path = cp.drop(CLASSPATH_PROTOCOL.length)
        getClass.getResource(path)
      case other => new URL(other)
    }
  }

//  case class RuleSchema(
//                         name: String,
//                         extractorType: String,
//                         labels: Seq[String],
//                         priority: String,
//                         action: Option[String],
//                         keep: Boolean,
//                         additional: Map[String, String],
//                         arguments: Seq[ArgumentSchema]


  // rule intermediary representation
  class Rule(
      val name: String,
      val labels: Seq[String],
      val ruleType: String,
      val unit: String,
      val priority: String,
      val keep: Boolean,
      val action: String,
      val pattern: String,
      val config: OdinConfig,
      val textOpt: Option[String] = None
  ) {

    val taxonomy: Option[Taxonomy] = config.taxonomy
    val resources = config.resources

    def copy(
      name: String = this.name,
      labels: Seq[String] = this.labels,
      ruleType: String = this.ruleType,
      unit: String = this.unit,
      priority: String = this.priority,
      keep: Boolean = this.keep,
      action: String = this.action,
      pattern: String = this.pattern,
      config: OdinConfig = this.config,
      textOpt: Option[String] = this.textOpt
    ): Rule = new Rule(name, labels, ruleType, unit, priority, keep, action, pattern, config, textOpt)
  }

  /**
    * Intermediate representation for a rule spanning multiple sentences
    * Produces a RelationMention with exactly two args: anchor and neighbor
    */
  class CrossSentenceRule(
    name: String,
    labels: Seq[String],
    ruleType: String,
    // the maximum number of sentences to look behind for pattern2
    val leftWindow: Int,
    // the maximum number of sentences to look ahead for pattern2
    val rightWindow: Int,
    unit: String,
    priority: String,
    keep: Boolean,
    action: String,
    pattern: String,
    config: OdinConfig,
    textOpt: Option[String] = None
  ) extends Rule(name, labels, ruleType, unit, priority, keep, action, pattern, config, textOpt)
}
