package edu.ut.cs.sdn.simpledns;

public class CIDRData {

    private int networkAddress;
    private int subnetMask;
    private String region;

    public CIDRData(int networkAddress, int subnetMask, String region) {
        this.networkAddress = networkAddress;
        this.subnetMask = subnetMask;
        this.region = region;
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


}