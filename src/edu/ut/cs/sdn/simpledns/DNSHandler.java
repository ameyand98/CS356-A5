package edu.ut.cs.sdn.simpledns;
import edu.ut.cs.sdn.simpledns.packet.*;
import java.util.*;
import java.io.*;
import java.net.*;

public class DNSHandler {

    private DNSClientConnection clientConnection;
    private static int DNS_PORT = 8053;
    private static int DNS_SEND_PORT = 53;
    private ArrayList<CIDRData> csvList;

    public DNSHandler(ArrayList<CIDRData> csvList) throws Exception {
        try {
            clientConnection = new DNSClientConnection(DNS_PORT);
            this.csvList = csvList;
        } catch (Exception e) {
            System.out.println("Exception: ");
            System.out.println(e);
        }
        
    }

    public void handleDNSRequest(InetAddress dnsServerIP) throws Exception {
        DatagramPacket dnsPacket = clientConnection.receiveDNSPacket();
        DNS request = DNS.deserialize(dnsPacket.getData(), dnsPacket.getLength());

        if (!isRequestValid(request)) {
            System.out.println("Invalid DNS request");
            return;
        }
        
        System.out.println("Valid Request Received");
        
        InetAddress clientIP = dnsPacket.getAddress();
        int clientPort = dnsPacket.getPort();

        DatagramPacket replyPacket;
        

        System.out.println("IP, Port, and Socket created");


        // TODO - Handle valid request, recursive or non-recursive
        if (request.isRecursionDesired()) {
            //recursively resolve
            System.out.println("Recursively Resolved");

            replyPacket = recursivelyResolve(dnsServerIP, dnsPacket, request);
        } else {
            //non-recursively resolve
            System.out.println("Non-recursively Resolved");
            replyPacket = nonRecursivelyResolve(dnsServerIP, dnsPacket, request);
        }

        DNS reply = DNS.deserialize(replyPacket.getData(), replyPacket.getLength());
        System.out.println("Reply Packet is: " + reply.toString());

        replyPacket.setPort(clientPort);
        replyPacket.setAddress(clientIP);

        clientConnection.sendDNSPacket(replyPacket);
    }

    public DatagramPacket recursivelyResolve(InetAddress rootAddr, DatagramPacket query, DNS req) throws Exception {
        boolean resolved = false;
        byte[] bytes = new byte[4096];
        return new DatagramPacket(bytes, bytes.length);
    }

    public DatagramPacket nonRecursivelyResolve(InetAddress rootAddr, DatagramPacket query, DNS req) throws Exception {
        DatagramPacket recvPacket;
        DatagramPacket newQuery = new DatagramPacket(query.getData(), query.getLength(), rootAddr, DNS_SEND_PORT);

        clientConnection.sendDNSPacket(newQuery);
        recvPacket = clientConnection.receiveDNSPacket();

        return recvPacket;
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
