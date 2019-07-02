package com.amazonaws.services.infrav.sink;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.google.common.base.Supplier;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.apache.flink.api.common.functions.RuntimeContext;
//import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch.util.RetryRejectedExecutionFailureHandler;
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.parsing.json.JSON;
import scala.util.parsing.json.JSONObject;
import vc.inreach.aws.request.AWSSigner;
import vc.inreach.aws.request.AWSSigningRequestInterceptor;

public class AmazonElasticSearchSink {
    private static final String ES_SERVICE_NAME = "es";

    private static final int FLUSH_MAX_ACTIONS = 1;
    private static final long FLUSH_INTERVAL_MILLIS = 10;
    private static final int FLUSH_MAX_SIZE_MB = 1;

    private static final Logger LOG = LoggerFactory.getLogger(AmazonElasticSearchSink.class);

    public static <T> ElasticsearchSink<T> buildElasticsearchSink(String elasticsearchEndpoint, String region, String indexName, String type) {
        final List<HttpHost> httpHosts = Arrays.asList(HttpHost.create(elasticsearchEndpoint));
        //final SerializableAWSSigningRequestInterceptor requestInterceptor = new SerializableAWSSigningRequestInterceptor(region);
        final ObjectMapper objectMapper = new ObjectMapper();
        ElasticsearchSink.Builder<T> esSinkBuilder = new ElasticsearchSink.Builder<>(
                httpHosts,
                new ElasticsearchSinkFunction<T>() {
                    public IndexRequest createIndexRequest(T element) throws Exception {

                        return Requests.indexRequest()
                                .index(indexName)
                                .type(type)
                                .source(objectMapper.writeValueAsString(element),XContentType.JSON);
                    }
                    @Override
                    public void process(T element, RuntimeContext ctx, RequestIndexer indexer) {
                        try {
                            indexer.add(createIndexRequest(element));
                        }catch (Exception e){}
                    }
                }
        );

        /* Uncomment the next values for bulk request
        esSinkBuilder.setBulkFlushMaxActions(FLUSH_MAX_ACTIONS);
        esSinkBuilder.setBulkFlushInterval(FLUSH_INTERVAL_MILLIS);
        esSinkBuilder.setBulkFlushMaxSizeMb(FLUSH_MAX_SIZE_MB);
        esSinkBuilder.setBulkFlushBackoff(true);
        */

        //Comment the line below. Just for testing.
        esSinkBuilder.setBulkFlushMaxActions(2);


      /*  esSinkBuilder.setRestClientFactory(
                restClientBuilder -> restClientBuilder.setHttpClientConfigCallback(callback -> callback.addInterceptorLast(requestInterceptor))
        );*/

        esSinkBuilder.setFailureHandler(new RetryRejectedExecutionFailureHandler());

        return esSinkBuilder.build();
    }


    static class SerializableAWSSigningRequestInterceptor implements HttpRequestInterceptor, Serializable {
        private final String region;
        private transient AWSSigningRequestInterceptor requestInterceptor;

        public SerializableAWSSigningRequestInterceptor(String region) {
            this.region = region;
        }

        @Override
        public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
            if (requestInterceptor == null) {
                final Supplier<LocalDateTime> clock = () -> LocalDateTime.now(ZoneOffset.UTC);
                final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
                final AWSSigner awsSigner = new AWSSigner(credentialsProvider, region, ES_SERVICE_NAME, clock);

                requestInterceptor = new AWSSigningRequestInterceptor(awsSigner);
            }

            requestInterceptor.process(httpRequest, httpContext);
        }
    }
}
