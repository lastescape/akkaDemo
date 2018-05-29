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
import com.alibaba.fastjson.{JSON, JSONObject}
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory

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

    final case class TaskStatusInfo(schedule_id: String,
                                    schedule_time: String,
                                    name: String,
                                    guid: String,
                                    schedule_time_offset: String,
                                    start_time: String,
                                    stop_time: String,
                                    status: String,
                                    today: String,
                                    gmt_date: String)

    implicit val TaskStatusInfoFormat = jsonFormat10(TaskStatusInfo)
    final case class TaskStatusList(code: Int, data: Array[TaskStatusInfo])
    implicit val TaskStatusListFormat = jsonFormat2(TaskStatusList)


    final case class TaskCodeInfoItem(id: String,
                                  name: String,
                                  description: String,
                                  `type`: String,
                                  owner: String,
                                  host: String,
                                  schedule_mode: String,
                                  schedule_interval: String,
                                  schedule_day: String,
                                  sequential: String,
                                  delay_execution: String,
                                  create_time: String
                                 )
    final case class TaskCodeInfo(code: Int, data: Array[TaskCodeInfoItem])



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

                                // Instantiate and configure the SslContextFactory
                                val sslContextFactory = new SslContextFactory()

                                // Instantiate HttpClient with the SslContextFactory
                                val httpClient = new HttpClient(sslContextFactory)

                                // Configure HttpClient, for example:
                                httpClient.setFollowRedirects(false)

                                // Start HttpClient
                                httpClient.start()


                                // get task code
                                val code_response = httpClient.GET(s"http://10.40.8.115:8080/api/v1/task_search?name_seg=${fieldMap.input_1}")
                                val code_responseContent = code_response.getContentAsString()
                                val task_code = JSON.parseObject[TaskCodeInfo](code_responseContent, classOf[TaskCodeInfo])

                                if (task_code != null) {
                                    val code = task_code.data(0).id
                                    val response = httpClient.GET(s"http://10.40.8.115:8080/api/v1/task_executions/${code}")
                                    val responseContent = response.getContentAsString()
                                    val taskStatusList = JSON.parseObject[TaskStatusList](responseContent, classOf[TaskStatusList])
                                    complete(taskStatusList)
                                } else {
                                    complete("")
                                }

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
