package com.amazonaws.services.test;

import com.amazonaws.services.infrav.events.es.CpuAgg;
import com.amazonaws.services.infrav.events.kinesis.VcenterMetric;
import com.amazonaws.services.infrav.sink.AmazonElasticSearchSink;
import com.google.common.collect.Iterables;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.StreamSupport;

public class SocketStream {
    private static final Logger LOG = LoggerFactory.getLogger(SocketStream.class);
    private static final List<String> MANDATORY_PARAMETERS = Arrays.asList("KinesisInputStream");

    private Properties prop = null;


    public static void main(String[] args) throws Exception {



        final int port;
        final ParameterTool params;
        try {
             params = ParameterTool.fromArgs(args);
            port = params.getInt("port");
        } catch (Exception e) {
            System.err.println("No port specified. Please run 'SocketWindowWordCount --port <port>'");
            return;
        }

        // get the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        SocketStream ss = new SocketStream();

        // get input data by connecting to the socket
        DataStream<String> text = env.socketTextStream("localhost", port, "\n");

        DataStream<CpuAgg> vcenterAggregates = text
                .map((String object) -> {
                    ObjectMapper mapper = new ObjectMapper();
                    VcenterMetric vcenterMetric = mapper.readValue(object, VcenterMetric.class);
                    return vcenterMetric;
                })
                .keyBy("name")
                .timeWindow(Time.seconds(10))
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
                });

        vcenterAggregates.addSink(AmazonElasticSearchSink.buildElasticsearchSink("127.0.0.1:9200", "eu-central-1", "metrics", "cpu"));


        env.setMaxParallelism(1).execute();

    }
}
