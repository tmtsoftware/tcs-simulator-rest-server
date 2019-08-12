package v1.gs

import akka.Done
import akka.util.Timeout
import csw.client.utils.Extensions.FutureExt
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.framework.CswClientWiring
import csw.framework.commons.CoordinatedShutdownReasons.ApplicationFinishedReason
import csw.location.api.models.ComponentType.{Assembly, HCD}
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaLocation, ComponentId, ComponentType}
import csw.params.commands.{CommandName, CommandResponse, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{ObsId, Prefix}
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}



/**
  * Takes HTTP requests and produces JSON.
  */
class GsController @Inject()(cc: GsControllerComponents)
  extends GsBaseController(cc) {

  //  private val logger = Logger(getClass)


  lazy val clientWiring = new CswClientWiring
  import clientWiring._
  import wiring._
  import actorRuntime._

  def assemblyCommandService(assemblyName: String): CommandService = createCommandService(getAkkaLocation(assemblyName, Assembly))

  def hcdCommandService(hcdName: String): CommandService = createCommandService(getAkkaLocation(hcdName, HCD))

  def shutdown(): Done = wiring.actorRuntime.shutdown(ApplicationFinishedReason).await()

  private def getAkkaLocation(name: String, cType: ComponentType): AkkaLocation = {
    val maybeLocation = locationService.resolve(AkkaConnection(ComponentId(name, cType)), timeout).await()
    maybeLocation.getOrElse(throw new RuntimeException(s"Location not found for component: name:[$name] type:[${cType.name}]"))
  }

  private def createCommandService: AkkaLocation ⇒ CommandService = CommandServiceFactory.make


  private val source:Prefix = Prefix("web-server")
  private val maybeObsId = None


  println("ABOUT TO GET ASSEMBLY COMMAND")
  private val assemblyCommand = assemblyCommandService("JTcssimulatorAssembly")



  //LoggingSystemFactory.start("GalilHcdClientApp", "0.1", host, system)


  // Assembly Commands

  def getStatusMetaData(): Action[AnyContent] = GsAction.async { implicit request =>
    implicit val ec: ExecutionContext = actorSystem.executionContext
    getStatusMetaDataFromHcd().map { response => {

      response match {
        case resp: CommandResponse.CompletedWithResult => {

          Ok(Json.toJson(resp.result));
        }
        case _ =>
          Ok(Json.toJson(response.toString))
      }

    }

    }
  }



  def getStatusMetaDataFromHcd(): Future[CommandResponse] = {

    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(3.seconds)
    val setup = Setup(source, CommandName("getStatusMetaData"), maybeObsId)

    assemblyCommand.submit(setup)
  }



}
