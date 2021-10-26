package pl.touk.nussknacker.engine.standalone

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.standalone.deployment.DeploymentError
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.io.File

// todo:
// parse config model
// read scenario json
// create LocalModelData
// parse scenario to EspProcess
// setup fatjar
// setup docker image

object NuEngineApp extends App {

  require(args.nonEmpty, "Scenario json should be passed as a first argument")

  val process = ProcessMarshaller.fromJson(args(0)).toValidatedNel[Any, CanonicalProcess] andThen { canonical =>
    ProcessCanonizer.uncanonize(canonical.withoutDisabledNodes)
  } match {
    case Valid(p) => p
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
  }

  val config = ConfigFactory.parseFile(new File(args(1))) // nu-engine.conf
  val modelData = ModelData.duringExecution(config)


  // below values need to be passed for EngineConfig
  //                          (pollDuration: FiniteDuration = 100 millis,
  //                          shutdownTimeout: Duration = 10 seconds,
  //                          interpreterTimeout: Duration = 10 seconds,
  //                          publishTimeout: Duration = 5 seconds,
  //                          exceptionHandlingConfig: KafkaExceptionConsumerConfig)

  // below values needed eventually by KafkaConfig
  //                       )kafkaAddress: String,
  //                       kafkaProperties: Option[Map[String, String]],
  //                       kafkaEspProperties: Option[Map[String, String]],
  //                       consumerGroupNamingStrategy: Option[ConsumerGroupNamingStrategy.Value] = None,
  //                       avroKryoGenericRecordSchemaIdSerialization: Option[Boolean] = None,
  //                       topicsExistenceValidationConfig: TopicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false),
  //                       useStringForKey: Boolean = true)


  // eventually the app wil launch class KafkaTransactionalScenarioInterpreter so it needs as arguments:
  // ModelData, EspProcess, JobData, EngineConfig


  // configuration file will be similar to standalone's application.conf where standaloneConfig=engineConfig, standaloneProcessConfig=modelData content


  //start with creating Integration Test for the whole thing with data hardcoded into the file, config files later

}
