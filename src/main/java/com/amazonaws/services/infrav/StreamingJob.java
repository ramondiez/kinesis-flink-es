package com.amazonaws.services.infrav;


import com.amazonaws.services.infrav.events.es.CpuAgg;
import com.amazonaws.services.infrav.events.kinesis.VcenterMetric;
import com.amazonaws.services.infrav.sink.AmazonElasticSearchSink;
import com.google.common.collect.Iterables;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.formats.json.JsonNodeDeserializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.StreamSupport;

public class StreamingJob {

    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);
    private static final List<String> MANDATORY_PARAMETERS = Arrays.asList("InputStreamName");


    public static void main(String[] args) throws Exception {

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // Processing time should be based on a field which will be extracted from the data as opposed
        // to being derived when the message is processed
        //env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //Define Kinesis Consumer properties
        Properties kinesisConsumerConfig = new Properties();
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.AWS_REGION, "eu-central-1");
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "1000");

        //Leverage JsonNodeDeserializationSchema to convert incoming JSON to generic ObjectNode
        DataStream<ObjectNode> vcenterMetricStreamObject =  env.addSource(new FlinkKinesisConsumer<>(
                "vcenter-stream",
                new JsonNodeDeserializationSchema(),
                kinesisConsumerConfig));



        //Map incomming data from the generic Object Node to a POJO object
        //Set if TimeCharacteristic = "EventTime" to determine how the the Time Attribute rowtime will be determined from the incoming data
        DataStream<CpuAgg> vcenterAggregates = vcenterMetricStreamObject
                .map((ObjectNode object) -> {
                    ObjectMapper mapper = new ObjectMapper();
                    VcenterMetric vcenterMetric = mapper.readValue(object.toString(), VcenterMetric.class);
                    LOG.info("faraKinesis2 {}",vcenterMetric.getName());
                    return vcenterMetric;
                })
                /* Esta line establece que los eventos se ingesten en base al tiempo de creación del evento y no a la llegada del evento.
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<VcenterMetric>() {
                            @Override
                            public long extractAscendingTimestamp(VcenterMetric element) {
                                return element.timestamp.getTime();
                           }})
                 */
                .keyBy("name")
                .timeWindow(Time.seconds(5))
                .apply(new WindowFunction<VcenterMetric, CpuAgg, Tuple, TimeWindow>() {
                    @Override
                    public void apply(Tuple tuple, TimeWindow timeWindow, Iterable<VcenterMetric> iterable, Collector<CpuAgg> collector) throws Exception {
                        if (Iterables.size(iterable) > 1){
                            String name = Iterables.get(iterable,0).name;
                            System.out.println(Iterables.get(iterable,0).fields.get("usage_average"));
                            DoubleSummaryStatistics stats = StreamSupport
                                    .stream(iterable.spliterator(), false)
                                    .mapToDouble(event -> event.fields.get("usage_average"))
                                    .summaryStatistics();
                            collector.collect(new CpuAgg(name,stats.getMax(),stats.getMin(),stats.getAverage(),timeWindow.getEnd()));
                        }
                    }
                });;

        // Save the aggregate to elasticsearch
        vcenterAggregates.addSink(AmazonElasticSearchSink.buildElasticsearchSink("localhost:9200", "eu-central-1", "metrics", "cpu"));

        env.execute();
    }
}
