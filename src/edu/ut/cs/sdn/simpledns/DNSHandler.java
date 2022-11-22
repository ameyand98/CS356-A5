package edu.ut.cs.sdn.simpledns;
import edu.ut.cs.sdn.simpledns.packet.*;
import java.util.*;
import java.io.*;
import java.net.*;

public class DNSHandler {

    private DNSClientConnection clientConnection;
    private static int DNS_PORT = 8053;
    private ArrayList<CIDRData> csvList;

    public DNSHandler(ArrayList<CIDRData> csvList) {
        clientConnection = new DNSClientConnection(DNS_PORT);
        this.csvList = csvList;
    }

    public void handleDNSRequest(InetAddress dnsServerIP) {
        DatagramPacket dnsPacket = clientConnection.receiveDNSPacket();
        DNS request = DNS.deserialize(dnsPacket.getData(), dnsPacket.getLength());

        if (!isRequestValid(request)) {
            System.out.println("Invalid DNS request");
            return;
        }

        InetAddress clientIP = dnsPacket.getAddress();
        int clientPort = dnsPacket.getPort();


        // TODO - Handle valid request, recursive or non-recursive

    }

    public boolean isRequestValid(DNS request) {
        boolean validQuestions = true;
        List<DNSQuestion> questionList = request.getQuestions();
        if (questionList.isEmpty()) {
            validQuestions = false;
        }
        int questionListSize = questionList.size();
        short recordType;
        for (int i = 0; i < questionListSize; i++) {
            DNSQuestion question = questionList.get(i);
            recordType = question.getType();
            if (recordType != DNS.TYPE_A && recordType != DNS.TYPE_AAAA && recordType != DNS.TYPE_CNAME && recordType != DNS.TYPE_NS) {
                validQuestions = false;
            }

        }
        return (validQuestions && request.getOpcode() == 0 && request.isQuery());
    }

}
