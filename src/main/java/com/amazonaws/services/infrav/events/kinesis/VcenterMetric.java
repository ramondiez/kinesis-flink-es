package com.amazonaws.services.infrav.events.kinesis;

import java.sql.Timestamp;
import java.util.Map;

public class VcenterMetric {
    public String name;
    public Timestamp timestamp;
    public Map<String,Double> fields;
    public Map<String,String> tags;
    private static final long serialVersionUID = 6529685098267757690L;

    public VcenterMetric() {
    }


    public VcenterMetric(String name, Timestamp timestamp, Map<String, Double> fields, Map<String, String> tags) {
        this.name = name;
        this.timestamp = timestamp;
        this.fields = fields;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public Map<String, Double> getFields() {
        return fields;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return name;
    }

}
