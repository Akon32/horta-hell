package ru.org.codingteam.horta.core

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import ru.org.codingteam.horta.actors.database._
import ru.org.codingteam.horta.messages.{CoreRoomLeave, CoreRoomJoin, CoreMessage}
import ru.org.codingteam.horta.plugins._
import ru.org.codingteam.horta.plugins.bash.BashPlugin
import ru.org.codingteam.horta.plugins.markov.MarkovPlugin
import ru.org.codingteam.horta.plugins.pet.PetPlugin
import ru.org.codingteam.horta.protocol.jabber.JabberProtocol
import ru.org.codingteam.horta.security._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.Some

/**
 * Horta core actor. Manages all plugins, routes global messages.
 */
class Core extends Actor with ActorLogging {

  import context.dispatcher

  implicit val timeout = Timeout(60 seconds)

  /**
   * List of plugin props to be started.
   */
  val plugins: List[Props] = List(
    Props[FortunePlugin],
    Props[AccessPlugin],
    Props[PetPlugin],
    Props[MarkovPlugin],
    Props[VersionPlugin],
    Props[BashPlugin]
  )

  /**
   * List of registered commands.
   */
  var commands = Map[String, List[(ActorRef, CommandDefinition)]]()

  /**
   * List of plugins receiving all the messages.
   */
  var messageReceivers = List[ActorRef]()

  /**
   * List of plugins receiving room notifications.
   */
  var roomReceivers = List[ActorRef]()

  /**
   * List of plugins receiving the user notifications.
   */
  var userReceivers = List[ActorRef]()

  val parsers = List(SlashParsers, DollarParsers)

  override def preStart() {
    val definitions = getPluginDefinitions
    parseNotifications(definitions)

    commands = Core.getCommands(definitions)
    commands foreach (command => log.info(s"Registered command: $command"))

    val storages = Core.getStorages(definitions)

    // TODO: What is the Akka way to create these?
    val store = context.actorOf(Props(classOf[PersistentStore], storages), "store")
    val protocol = context.actorOf(Props[JabberProtocol], "jabber")
  }

  override def receive = {
    case CoreMessage(credential, text) => processMessage(credential, text)
    case CoreRoomJoin(roomJID, actor) => processRoomJoin(roomJID, actor)
    case CoreRoomLeave(roomJID) => processRoomLeave(roomJID)
  }

  private def getPluginDefinitions: List[(ActorRef, PluginDefinition)] = {
    val responses = Future.sequence(for (plugin <- plugins) yield {
      val actor = context.actorOf(plugin)
      ask(actor, GetPluginDefinition).mapTo[PluginDefinition].map(definition => (actor, definition))
    })
    Await.result(responses, Duration.Inf)
  }

  private def parseNotifications(definitions: List[(ActorRef, PluginDefinition)]) = {
    for ((actor, definition) <- definitions) {
      definition.notifications match {
        case Notifications(messages, rooms, users) =>
          if (messages) {
            messageReceivers ::= actor
          }
          if (rooms) {
            roomReceivers ::= actor
          }
          if (users) {
            userReceivers ::= actor
          }
      }
    }
  }

  private def processMessage(credential: Credential, text: String) {
    val command = parseCommand(text)
    command match {
      case Some((name, arguments)) =>
        executeCommand(sender, credential, name, arguments)
      case None =>
    }

    for (plugin <- messageReceivers) {
      plugin ! ProcessMessage(credential, text)
    }
  }

  private def processRoomJoin(roomJID: String, actor: ActorRef) {
    for (plugin <- roomReceivers) {
      plugin ! ProcessRoomJoin(roomJID, actor)
    }
  }

  private def processRoomLeave(roomJID: String) {
    for (plugin <- roomReceivers) {
      plugin ! ProcessRoomLeave(roomJID)
    }
  }

  private def parseCommand(message: String): Option[(String, Array[String])] = {
    for (p <- parsers) {
      p.parse(p.command, message) match {
        case p.Success((name, arguments), _) => return Some((name.asInstanceOf[String], arguments.asInstanceOf[Array[String]]))
        case _ =>
      }
    }

    None
  }

  /**
   * Executes the command.
   * @param credential credential of user who has sent the command.
   * @param name command name.
   * @param arguments command arguments.
   */
  private def executeCommand(sender: ActorRef, credential: Credential, name: String, arguments: Array[String]) {
    val executors = commands.get(name)
    executors match {
      case Some(executors) =>
        executors foreach {
          case (plugin, CommandDefinition(level, _, token)) if accessGranted(level, credential) =>
            plugin ! ProcessCommand(credential, token, arguments)
        }
      case None =>
    }
  }

  private def accessGranted(access: AccessLevel, user: Credential) = {
    access match {
      case GlobalAccess => user.access == GlobalAccess
      case RoomAdminAccess => user.access == GlobalAccess || user.access == RoomAdminAccess
      case CommonAccess => true
    }
  }

}

object Core {

  private def getCommands(pluginDefinitions: List[(ActorRef, PluginDefinition)]
                           ): Map[String, List[(ActorRef, CommandDefinition)]] = {
    val commands = for (definition <- pluginDefinitions) yield {
      val actor = definition._1
      val pluginDefinition = definition._2
      for (command <- pluginDefinition.commands) yield (command.name, actor, command)
    }

    val groups = commands.flatMap(identity).groupBy(_._1).map(tuple => (tuple._1, tuple._2.map {
      case (_, actor, command) => (actor, command)
    }))

    groups
  }

  private def getStorages(pluginDefinitions: List[(ActorRef, PluginDefinition)]): Map[String, DAO] = {
    pluginDefinitions.map(_._2).filter(_.dao.isDefined).map(definition => (definition.name, definition.dao.get)).toMap
  }

}
