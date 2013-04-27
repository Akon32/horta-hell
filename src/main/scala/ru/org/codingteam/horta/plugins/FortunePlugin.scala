package ru.org.codingteam.horta.plugins

import ru.org.codingteam.horta.security.{CommandContext, Scope, GlobalScope}
import scala.util.parsing.json._
import scala.io.Source

class FortunePlugin extends CommandPlugin {
	private object FortuneCommand

	// see: http://www.iheartquotes.com/api
	private val apiCommand = "http://www.iheartquotes.com/api/v1/random"
	private val maxCharacters = 256
	private val sources = List(
		"esr",
		"humorix_stories",
		"joel_on_software",
		"math",
		"mav_flame",
		"osp_rules",
		"paul_graham",
		"prog_style",
		"subversion"
	).mkString("+")
	private val arguments = List(
		s"max_characters=${maxCharacters}",
		s"format=json",
		s"source=${sources}"
	).mkString("&")
	private val fortuneUrl = s"${apiCommand}?${arguments}"

	def commandDefinitions: List[CommandDefinition] =
		List(CommandDefinition(GlobalScope, "fortune", FortuneCommand))

	def processCommand (
		token: Any,
		scope: Scope,
		context: CommandContext,
		arguments: Array[String]
	): Option[String] = token match {
		case FortuneCommand =>
			try {
				val rawText = Source.fromURL(fortuneUrl).mkString
				val json = JSON.parseFull(rawText)
				val map = json.get.asInstanceOf[Map[String, Any]]
				val source = map.get("source").map(_.asInstanceOf[String])
				val quote = map.get("quote").map(_.asInstanceOf[String])
				(source, quote) match {
					case (Some(s), Some(q)) => Some(s"$q [$s]")
					case _ => Some("Something is not good.")
				}
			} catch {
				case e: Exception => {
					e.printStackTrace()
					Some("[ERROR] Something's wrong!")
				}
			}

		case _ => None
	}
}
