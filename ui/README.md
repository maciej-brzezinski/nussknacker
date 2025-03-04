# Running UI app to develop/debug Nussknacker

Points below describe setup of running Nussknacker UI during development of Nussknacker itself.
If you want to run UI to develop/debug your own model, please skip to last section.

## Building required modules to run from shell/IDE

Before running either from console or from IDE you have to manually build:
- run `npm ci` in `ui/client` (only if you want to test/compile FE, see `Readme.md` in `ui/client` for more details)
- custom models (```assemblySamples``` in sbt - not needed if running from IDE with stubbed ProcessManager, see below)
- ProcessManager(s) (```assemblyEngines``` in sbt - not needed if running from IDE with stubbed ProcessManager, see below)
- UI (```ui/assembly``` in sbt, not needed if you want to use FE development mode)
You can do all steps at once with ```buildServer.sh``` script

## Running from IntelliJ:
1. Find class `pl.touk.nussknacker.ui.NussknackerApp`
2. Edit run [configuration](https://www.jetbrains.com/help/idea/run-debug-configurations.html)

    * Main class:         pl.touk.nussknacker.ui.NussknackerApp
    * VM options:         -Dnussknacker.config.locations=../../../nussknacker-dist/src/universal/conf/dev-application.conf -Dlogback.configurationFile=../logback-dev.xml
    * Working directory:  should be set to ui/server/work
    * Environment variables: 
```AUTHENTICATION_USERS_FILE=../../../nussknacker-dist/src/universal/conf/users.conf;MANAGEMENT_MODEL_DIR=../../../engine/flink/management/sample/target/scala-2.12;GENERIC_MODEL_DIR=../../../engine/flink/generic/target/scala-2.12;STANDALONE_MODEL_DIR=../../../engine/standalone/engine/sample/target/scala-2.12```
If you want to connect to infrastructure in docker you need to set on end of line also:
```;FLINK_REST_URL=http://localhost:3031;FLINK_QUERYABLE_STATE_PROXY_URL=localhost:3063;SCHEMA_REGISTRY_URL=http://localhost:3082;KAFKA_ADDRESS=localhost:3032```
    * Module classpath:  nussknacker-ui (this is ```ui/server``` folder) 
    * "Included dependencies with "Provided" scope" should be checked, so that Flink ProcessManager is included in the classpath

## Running backend for frontend development
If you want run backend only for front-end development, please run `./runServer.sh`

## Running full env (for integration tests)
* Go to docker/demo and run `docker-compose -f docker-compose-env.yml up -d` - runs full env with kafka / flink / etc..
* Run nussknacker by IntelliJ or `USE_DOCKER_ENV=true ./runServer.sh`
 
## Access to service
 Service should be available at ~~http://localhost:8080/api~~

# Running UI to develop/debug additional models with stubbed ProcessManager

If you want to run Nussknacker UI to see if your model behaves correctly (e.g. if dynamic parameters are OK),
you don't have to follow steps above. You also don't have to build model jar and put it in
docker container or on filesystem.

You can run Nussknacker UI with your model in IDE via 
helper class `LocalNussknackerWithSingleModel`. To use it, add `nussknacker-ui` module to 
test classpath and prepare class similar to `RunGenericModelLocally`. 
It can be run from e.g. Intellij without special configuration and it will run sample 
Nussknacker UI config with your model.
