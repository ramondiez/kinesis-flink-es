# Flink for InfraV pipeline
Este proyecto quiere demostrar cómo se puede crear un pipeline de ingesta (Kinesis) análisis (Flink) y almacenamiento (Elasticsearch)

## Caso de uso
El caso de uso quiere demostrar cómo las trazas de Vcenter son ingestadas mediante Kinesis, después se realiza una agregación en Flink, para acabar mandando los logs a Elasticsearch para su posterior visualización.

## Dependencias

```
https://docs.aws.amazon.com/kinesisanalytics/latest/java/getting-started.html#setting-up-prerequisites
```

### Add the Apache Flink Kinesis connector to your local environment
El conector de Kinesis para Flink tiene una dependencia bajo la licencia de [Amazon Software License](https://aws.amazon.com/asl/) (ASL) por lo que no es posible descargar el artefacto de maven.  Para descargar y compilar el conector, haz los siguientes pasos:

* Descargar la versión de Flink que se quiera usar.
```
wget https://github.com/apache/flink/archive/release-1.8.0.zip
```
* Descomprimir el paquete

* Acceder a la carpeta de Flink y ejecutar el siguiente comando.
```
mvn clean install -Pinclude-kinesis -DskipTests
```
* En nuestro proyecto de Maven podremos añadir el artefacto.
```
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-connector-kinesis_2.11</artifactId>
    <version>1.8.0</version>
</dependency>
```

### Create un datastream de prueba
Create 1 Amazon Kinesis Data Streams.

```
$ aws kinesis create-stream \
--stream-name vcenter-stream \
--shard-count 1 \
--region eu-central-1 \

```

### Usar datos de ejemplo
Kinesis Data Generator (https://awslabs.github.io/amazon-kinesis-data-generator/web/help.html) nos da la opción de mandar trazas en base a unos templates.

* For the order stream, configure the following template at a rate of 100 records per second.
```
{
  "fields": {
    "usage_average": {{random.number(50)}}
  },
  "name": "{{random.arrayElement(["vsphere_host_mem","vsphere_host_cpu"])}}",
  "tags": {
    "clustername": "{{random.arrayElement(["CL_MADLBP_HP_01","CL_MADLBP_HP_02","CL_MADLBP_HP_03"])}}"
  },
  "timestamp": {{date.now("X")}}
}
```


### Descargar y paquetizar la aplicación
* Descargar el código de github
```
git clone https://github.com/aws-samples/amazon-kinesis-data-analytics-flinktableapi
```
* Navigate to the newly created *amazon-kinesis-data-analytics-flinktableapi* directory containing the pom.xml file.  Execute the following command to create your JAR file.
```
mvn package
```
* Si la aplicación compila correctamente la siguiente fichero será creado.  
```
target/aws-kinesis-analytics-java-apps-1.0.jar
```
* At this point, proceed with the getting started guide to upload and start your application
```
https://docs.aws.amazon.com/kinesisanalytics/latest/java/get-started-exercise.html#get-started-exercise-6
```

### Development Environment Setup
You can inspect and modify the code by modifying the .java files located within the project tree.  In my development, I used IntelliJ IDEA from Jetbrains. I followed the steps listed within the Apache Flink Documentation to setup my environment.
```
https://ci.apache.org/projects/flink/flink-docs-stable/flinkDev/ide_setup.html
```
Once the cloned project is imported as a Maven project, to be able to run and debug the application within the IDE, you must conduct an additional step of settting the project runtime configuration.  Add a configuration using the *Application* template and set the following parameters
1. *Main class* - com.amazonaws.services.kinesisanalytics.StreamingJob
1. *Working directory* - $MODULE_WORKING_DIR$
1. *Use classpath of module* - aws-kinesis-analytics-java-apps
1. *JRE* - Default (1.8 - SDK of 'aws-kinesis-analytics-java-apps' module)
