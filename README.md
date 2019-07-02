# Flink for InfraV pipeline
Este proyecto quiere demostrar cómo se puede crear un pipeline de ingesta (Kinesis) análisis (Flink) y almacenamiento (Elasticsearch)

## Caso de uso
El caso de uso quiere demostrar cómo las trazas de Vcenter son ingestadas mediante Kinesis, después se realiza una agregación en Flink, para acabar mandando los logs a Elasticsearch para su posterior visualización.

## Dependencias

```
https://docs.aws.amazon.com/kinesisanalytics/latest/java/getting-started.html#setting-up-prerequisites
```

## Entorno
El siguiente ejemplo despliega mediante un docker-compose un Elasticsearch + Kibana + Flink para poder ejecutar los ejemplos en local.
Para ello es necesario que esté instalado la utilidad de docker-compose

* Abrir una terminal en la raíz del proyecto y ejecutar el siguiente comando.
```
docker-compose up -d
```
Este comando cargará el siguiente fichero:

```
version: '3'

services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:7.0.1
    container_name: elasticsearch
    environment:
        - node.name=elasticsearch
        - discovery.seed_hosts=elasticsearch
        - cluster.initial_master_nodes=elasticsearch
        - cluster.name=docker-cluster
        - bootstrap.memory_lock=true
        - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - esdata:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"

  kibana:
    image: docker.elastic.co/kibana/kibana:7.0.1
    container_name: kibana
    depends_on: ['elasticsearch']
    ports:
      - "5601:5601"

  jobmanager:
    image: flink:1.8.0
    expose:
      - "6123"
    ports:
      - "8081:8081"
    command: jobmanager
    environment:
      - JOB_MANAGER_RPC_ADDRESS=jobmanager

  taskmanager:
    image: flink:1.8.0
    expose:
      - "6121"
      - "6122"
    depends_on:
      - jobmanager
    command: taskmanager
    links:
      - "jobmanager:jobmanager"
    environment:
      - JOB_MANAGER_RPC_ADDRESS=jobmanager

```

Elasticsearch estará disponible en http://localhost:9200/
Kibana UI estará disponible en http://localhost:5601/
Flink estará disponible en http://localhost:8081

## Creación del índice en ES
En el directorio de utils-->ElasticSearch hay un documento que explica cómo generar un índice y su respectivo mapping en ElasticSearch

```
cat ~/mapping.json | curl -XPUT "http://localhost:9200/metrics" -H 'Content-Type: application/json' -d @-
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
Kinesis Data Generator (https://awslabs.github.io/amazon-kinesis-data-generator/web/help.html) nos da la opción de mandar registros de Kinesis en base a unos templates.

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
git clone https://github.com/ramondiez/kinesis-flink-es.git
```
* En fichero pom hay una entrada para especificar la clase **Main** que hay que ejecutar.
```
<transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
<mainClass>com.amazonaws.services.infrav.StreamingJob</mainClass>
</transformer>
```

* Navega has el directorio *kinesis-flink-es* que contiene el fichero  pom.xml file.  Y ejecutar el siguiente comando para obtener el fichero jar.
```
mvn package
```
* Si la aplicación compila correctamente la siguiente fichero será creado.  
```
target/kinesis-flink-es-1.0.jar
```
* Una vez tengamos el fichero jar, deberemos subirlo a flink y ejecutarlo
```
$ JOBMANAGER_CONTAINER=$(docker ps --filter name=jobmanager --format={{.ID}})
$ docker cp target/kinesis-flink-es-1.0.jar "$JOBMANAGER_CONTAINER":/job.jar
$ docker exec -t -i "$JOBMANAGER_CONTAINER" flink run /job.jar
```
