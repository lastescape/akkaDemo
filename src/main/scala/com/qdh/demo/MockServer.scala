package com.qdh.demo

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.collection.mutable
import scala.io.StdIn

/**
  * Created by qdh on 2018/5/28.
  */
object MockServer {
    val settings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange.*
    )

    //model
    final case class User(name: String, age: Int, addr: String)
    implicit val itemFormat = jsonFormat3(User)

    final case class UserGroup(items: List[User])
    implicit val orderFormat = jsonFormat1(UserGroup)

    final case class TempClass(input_1: String, input_2: String)
    implicit val tempClassFormat = jsonFormat2(TempClass)

    private val userGroup = mutable.ListBuffer[User]()

    def main(args: Array[String]): Unit = {

        implicit val system = ActorSystem("mock_system")
        implicit val materializer = ActorMaterializer()

        implicit val executionContext = system.dispatcher

        val route: Route =
            (path("hello") & get & cors(settings)){
                complete("hello akka")
            } ~
            (path("list_all") & cors(settings)){
                  userGroup.clear()
                  userGroup += User("jack", 18, "NewYork")
                  userGroup += User("mike", 21, "paris")
                  val user_group = UserGroup(this.userGroup.toList)
                  complete(user_group)
            } ~
            get {
                (pathPrefix("user" / IntNumber) & cors(settings)){
                    age => {
                        val user = User("lucy", age.toInt, "tokyo")
                        complete(user)
                    }
                }
            } ~
            post {
                (path("postTest") & cors(settings)) {
                    entity(as[TempClass]) {
                        fieldMap => {
                            extractClientIP { ip =>
                              complete(fieldMap)
                            }
                        }
                    }
                }
            }

        val bindingFuture = Http().bindAndHandle(route, "localhost", 7070)

        println(s"Server online at http://localhost:7070/\nPress RETURN to stop...")
        StdIn.readLine() // let it run until user presses return

        bindingFuture
          .flatMap(_.unbind()) // trigger unbinding from the port
          .onComplete(_ => system.terminate()) // and shutdown when done


    }
}
