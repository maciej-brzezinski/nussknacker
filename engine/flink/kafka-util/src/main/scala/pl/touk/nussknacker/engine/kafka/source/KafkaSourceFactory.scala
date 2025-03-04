package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka._
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, DefinedSingleParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{WithExplicitTypesToExtract, _}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceFactoryState
import pl.touk.nussknacker.engine.kafka.validator.WithCachedTopicsExistenceValidator

import scala.reflect.ClassTag

/**
  * Base factory for Kafka sources with additional metadata variable.
  * It is based on [[pl.touk.nussknacker.engine.api.context.transformation.SingleInputGenericNodeTransformation]]
  * that allows custom ValidationContext and Context transformations, which are provided by [[pl.touk.nussknacker.engine.kafka.source.KafkaContextInitializer]]
  * Can be used for single- or multi- topic sources (as csv, see topicNameSeparator and extractTopics).
  *
  * Wrapper for [[org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer]]
  * Features:
  *   - fetch latest N records which can be later used to test process in UI
  * Fetching data is defined in [[pl.touk.nussknacker.engine.kafka.source.KafkaSource]] which
  * extends [[pl.touk.nussknacker.engine.api.process.TestDataGenerator]]. See [[pl.touk.nussknacker.engine.kafka.KafkaUtils#readLastMessages]]
  *   - reset Kafka's offset to latest value - `forceLatestRead` property, see [[pl.touk.nussknacker.engine.kafka.KafkaUtils#setOffsetToLatest]]
  *
  * @param deserializationSchemaFactory - produces KafkaDeserializationSchema for raw [[pl.touk.nussknacker.engine.kafka.source.KafkaSource]]
  * @param timestampAssigner            - provides timestampAsigner and WatermarkStrategy to KafkaSource
  * @param formatterFactory             - support for test data parser and generator
  * @param processObjectDependencies    - dependencies required by the component
  * @tparam K - type of key of kafka event that is generated by raw source (SourceFunction).
  * @tparam V - type of value of kafka event that is generated by raw source (SourceFunction).
  * */
class KafkaSourceFactory[K: ClassTag, V: ClassTag](deserializationSchemaFactory: KafkaDeserializationSchemaFactory[ConsumerRecord[K, V]],
                                                   timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                                   formatterFactory: RecordFormatterFactory,
                                                   processObjectDependencies: ProcessObjectDependencies)
  extends FlinkSourceFactory[ConsumerRecord[K, V]]
    with SingleInputGenericNodeTransformation[FlinkSource[ConsumerRecord[K, V]]]
    with WithCachedTopicsExistenceValidator
    with WithExplicitTypesToExtract {

  protected val topicNameSeparator = ","

  protected val keyTypingResult: TypedClass = Typed.typedClass[K]

  protected val valueTypingResult: TypedClass = Typed.typedClass[V]

  // Node validation and compilation refers to ValidationContext, that returns TypingResult's of all variables returned by the source.
  // Variable suggestion uses DefinitionExtractor that requires proper type definitions for GenericNodeTransformation (which in general does not have a specified "returnType"):
  // - for TypeClass (which is a default scenario) - it is necessary to provide all explicit TypeClass definitions as possibleVariableClasses
  // - for TypedObjectTypingResult - suggested variables are defined as explicit "fields"
  // Example:
  // - validation context indicates that #input is TypedClass(classOf(SampleProduct)), that is used by node compilation and validation
  // - definition extractor provides detailed definition of "pl.touk.nussknacker.engine.management.sample.dto.SampleProduct"
  // See also ProcessDefinitionExtractor.
  def typesToExtract: List[TypedClass] = List(keyTypingResult, valueTypingResult)

  override type State = KafkaSourceFactoryState[K, V, DefinedParameter]

  // initialParameters should not expose raised exceptions.
  override def initialParameters: List[Parameter] =
    try {
      prepareInitialParameters
    } catch {
      case e: Exception => handleExceptionInInitialParameters
    }

  protected def handleExceptionInInitialParameters: List[Parameter] = Nil

  private def initialStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case step@TransformationStep(Nil, _) =>
      NextParameters(prepareInitialParameters)
  }

  protected def topicsValidationErrors(topic: String)(implicit nodeId: ProcessCompilationError.NodeId): List[ProcessCompilationError.CustomNodeError] = {
      val topics = topic.split(topicNameSeparator).map(_.trim).toList
      val preparedTopics = topics.map(KafkaUtils.prepareKafkaTopic(_, processObjectDependencies)).map(_.prepared)
      validateTopics(preparedTopics).swap.toList.map(_.toCustomNodeError(nodeId.id, Some(KafkaSourceFactory.TopicParamName)))
  }

  protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case step@TransformationStep((KafkaSourceFactory.TopicParamName, DefinedEagerParameter(topic: String, _)) :: _, None) =>
      prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, valueTypingResult, topicsValidationErrors(topic))
    case step@TransformationStep((KafkaSourceFactory.TopicParamName, _) :: _, None) =>
      // Edge case - for some reason Topic is not defined, e.g. when topic does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def prepareSourceFinalResults(context: ValidationContext,
                                          dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          keyTypingResult: TypingResult,
                                          valueTypingResult: TypingResult,
                                          errors: List[ProcessCompilationError]
                                         )(implicit nodeId: NodeId): FinalResults = {
    val kafkaContextInitializer = prepareContextInitializer(parameters, keyTypingResult, valueTypingResult)
    FinalResults(
      finalContext = kafkaContextInitializer.validationContext(context, dependencies, parameters),
      errors = errors,
      state = Some(KafkaSourceFactoryState(kafkaContextInitializer)))
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(context: ValidationContext,
                                         dependencies: List[NodeDependencyValue],
                                         parameters: List[(String, DefinedParameter)],
                                         errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = KafkaContextInitializer.initializerWithUnknown[K, V, DefinedParameter]
    FinalResults(initializerWithUnknown.validationContext(context, dependencies, parameters), errors, None)
  }

  // Overwrite this for dynamic type definitions.
  protected def prepareContextInitializer(params: List[(String, DefinedParameter)],
                                          keyTypingResult: TypingResult,
                                          valueTypingResult: TypingResult): KafkaContextInitializer[K, V, DefinedParameter] =
    new KafkaContextInitializer[K, V, DefinedSingleParameter](keyTypingResult, valueTypingResult)

  /**
    * contextTransformation should handle exceptions raised by prepareInitialParameters
    */
  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId)
  : NodeTransformationDefinition =
    initialStep(context, dependencies) orElse
      nextSteps(context ,dependencies)

  /**
    * Common set of operations required to create basic KafkaSource.
    */
  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[ConsumerRecord[K, V]] = {
    val topics = extractTopics(params)
    val preparedTopics = topics.map(KafkaUtils.prepareKafkaTopic(_, processObjectDependencies))
    val deserializationSchema = deserializationSchemaFactory.create(topics, kafkaConfig)
    val formatter = formatterFactory.create(kafkaConfig, deserializationSchema)
    val contextInitializer = finalState.get.contextInitializer
    createSource(params, dependencies, finalState, preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, contextInitializer)
  }

  /**
    * Basic implementation of new source creation. Override this method to create custom KafkaSource.
    */
  protected def createSource(params: Map[String, Any],
                             dependencies: List[NodeDependencyValue],
                             finalState: Option[State],
                             preparedTopics: List[PreparedKafkaTopic],
                             kafkaConfig: KafkaConfig,
                             deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                             timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                             formatter: RecordFormatter,
                             flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]]): FlinkSource[ConsumerRecord[K, V]] = {
    new KafkaSource[ConsumerRecord[K, V]](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter) {
      override val contextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]] = flinkContextInitializer
    }
  }

  /**
    * Basic implementation of definition of single topic parameter.
    * In case of fetching topics from external repository: return list of topics or raise exception.
    */
  protected def prepareInitialParameters: List[Parameter] = topicParameter.parameter :: Nil

  protected val topicParameter: ParameterWithExtractor[String] =
    ParameterWithExtractor.mandatory[String](
      KafkaSourceFactory.TopicParamName,
      _.copy(validators = List(MandatoryParameterValidator, NotBlankParameterValidator))
    )

  /**
    * Extracts topics from default topic parameter.
    */
  protected def extractTopics(params: Map[String, Any]): List[String] = {
    val paramValue = topicParameter.extractValue(params)
    paramValue.split(topicNameSeparator).map(_.trim).toList
  }

  override def nodeDependencies: List[NodeDependency] = Nil

  override protected val kafkaConfig: KafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
}

object KafkaSourceFactory {
  final val TopicParamName = "topic"

  case class KafkaSourceFactoryState[K, V, DefinedParameter <: BaseDefinedParameter](contextInitializer: KafkaContextInitializer[K, V, DefinedParameter])
}
