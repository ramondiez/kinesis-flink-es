package com.amazonaws.services.infrav.events.es;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public abstract class Document {
    private static final long serialVersionUID = 6529685098267757690L;

    private static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public final long timestamp;

    public Document(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return gson.toJson(this);
    }
}