package edu.ut.cs.sdn.simpledns;

public class CIDRData {

    private int networkAddress;
    private int subnetMask;
    private String region;
    private int size;

    public CIDRData(int networkAddress, int subnetMask, String region, int size) {
        this.networkAddress = networkAddress;
        this.subnetMask = subnetMask;
        this.region = region;
        this.size = size;
    }

    public String getRegion() {
        return this.region;
    }

    public int getNetworkAddress() {
        return this.networkAddress;
    }

    public int getSubnetMask() {
        return this.subnetMask;
    }

    public int size() {
        return this.size;
    }


}