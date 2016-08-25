package pl.touk.esp.engine.marshall

import pl.touk.esp.engine.compile

sealed trait ProcessUnmarshallError {

  def nodeIds: Set[String]

}

sealed trait ProcessValidationError extends ProcessUnmarshallError

object ProcessUnmarshallError {

  case class ProcessJsonDecodeError(msg: String) extends ProcessUnmarshallError {
    override val nodeIds: Set[String] = Set.empty
  }

  case class ProcessCompilationError(nested: compile.ProcessCompilationError)  extends ProcessValidationError {
    override def nodeIds: Set[String] = nested.nodeIds
  }

}
