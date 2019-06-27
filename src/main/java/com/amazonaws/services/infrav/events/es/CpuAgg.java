package com.amazonaws.services.infrav.events.es;



public class CpuAgg extends Document {

    public final String name;
    public final double maxCPU;
    public final double minCPU;
    public final double avgCPU;
    private static final long serialVersionUID = 6529685098267757690L;

    public CpuAgg(String name, double maxCPU, double minCPU, double avgCPU, long timestamp) {
        super(timestamp);
        this.name = name;
        this.maxCPU = maxCPU;
        this.minCPU = minCPU;
        this.avgCPU = avgCPU;
    }

}
