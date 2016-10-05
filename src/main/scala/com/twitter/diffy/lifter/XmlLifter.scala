package com.twitter.diffy.lifter

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.parser.Parser
import org.jsoup.select.Elements

import scala.collection.JavaConversions._

object XmlLifter {
  def lift(node: Element): FieldMap[Any] = node match {
    case doc: Document =>
      val root = doc.select(":root").get(0)
      FieldMap(
        Map(
          root.tagName -> lift(root)
        )
      )
    case doc: Element =>
      val children: Elements = doc.children
      val attributes =
        FieldMap[String](
          doc.attributes.asList map { attribute =>
            attribute.getKey -> attribute.getValue
          } toMap
        )
      FieldMap(
        Map(
          "tag"         -> doc.tagName,
          "text"        -> doc.ownText,
          "attributes"  -> attributes,
          doc.tagName   -> children.map(element => lift(element))
        )
      )
  }

  def decode(xml: String): Document = Jsoup.parse(xml, "", Parser.xmlParser())
}