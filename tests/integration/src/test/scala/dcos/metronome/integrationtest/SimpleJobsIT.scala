package dcos.metronome.integrationtest

import play.api.libs.json.{ JsArray, JsObject }

import scala.concurrent.duration._

class SimpleJobsIT extends MetronomeITBase {

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  "A job run should complete" in withFixture() { f =>
    When("A job description is posted")
    val appId = "my-job"
    val jobDef =
      s"""
        |{
        |  "id": "${appId}",
        |  "description": "A job that sleeps",
        |  "run": {
        |    "cmd": "sleep 60",
        |    "cpus": 0.01,
        |    "mem": 32,
        |    "disk": 0
        |  }
        |}
      """.stripMargin

    val resp = f.metronome.createJob(jobDef)

    Then("The response should be OK")
    resp.value.status.intValue() shouldBe 201

    When("A job run is started")
    val startRunResp = f.metronome.startRun(appId)

    Then("The response should be OK")
    startRunResp.value.status.intValue() shouldBe 201

    eventually(timeout(30.seconds)) {
      val runsJson = f.metronome.getRuns(appId)
      runsJson.value.status.intValue() shouldBe 200
      val runs = runsJson.entityJson.as[JsArray]
      runs.value.length shouldBe 1

      val run = runs.value.head.as[JsObject]
      val status = run.value("status").as[String]
      status shouldBe "ACTIVE"
    }

  }

}
