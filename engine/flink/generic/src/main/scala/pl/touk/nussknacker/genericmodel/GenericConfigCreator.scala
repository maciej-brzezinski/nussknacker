package pl.touk.nussknacker.genericmodel

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandlerFactory
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.outer.OuterJoinTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{DelayTransformer, PeriodicSourceFactory, PreviousValueTransformer, UnionTransformer, UnionWithMemoTransformer}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")
  protected val avroSerializingSchemaRegistryProvider: SchemaRegistryProvider = createAvroSchemaRegistryProvider
  protected val jsonSerializingSchemaRegistryProvider: SchemaRegistryProvider = createJsonSchemaRegistryProvider

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate-sliding" -> defaultCategory(SlidingAggregateTransformerV2),
    "aggregate-tumbling" -> defaultCategory(TumblingAggregateTransformer),
    "aggregate-session" -> defaultCategory(SessionWindowAggregateTransformer),
    "outer-join" -> defaultCategory(OuterJoinTransformer),
    "union" -> defaultCategory(UnionTransformer),
    "union-memo" -> defaultCategory(UnionWithMemoTransformer),
    "delay" -> defaultCategory(DelayTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies)),
      "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(new KafkaAvroSourceFactory(avroSerializingSchemaRegistryProvider, processObjectDependencies, None)),
      "kafka-registry-typed-json" -> defaultCategory(new KafkaAvroSourceFactory(jsonSerializingSchemaRegistryProvider, processObjectDependencies, None)),
      "periodic" -> defaultCategory(PeriodicSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactoryWithEditor(avroSerializingSchemaRegistryProvider, processObjectDependencies)),
      "kafka-avro-raw" -> defaultCategory(new KafkaAvroSinkFactory(avroSerializingSchemaRegistryProvider, processObjectDependencies)),
      "kafka-registry-typed-json" -> defaultCategory(new KafkaAvroSinkFactoryWithEditor(jsonSerializingSchemaRegistryProvider, processObjectDependencies)),
      "kafka-registry-typed-json-raw" -> defaultCategory(new KafkaAvroSinkFactory(jsonSerializingSchemaRegistryProvider, processObjectDependencies)),
      "dead-end" -> defaultCategory(SinkFactory.noParam(EmptySink))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ConfigurableExceptionHandlerFactory(processObjectDependencies)

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "CONV" -> defaultCategory(conversion),
        "DATE" -> defaultCategory(date),
        "UTIL" -> defaultCategory(util)
      ),
      List()
    )
  }

  override def buildInfo(): Map[String, String] = {
    pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => k -> v.toString } + ("name" -> "generic")
  }

  protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()
  protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(CachedConfluentSchemaRegistryClientFactory())
}
