package edu.ut.cs.sdn.simpledns;
import edu.ut.cs.sdn.simpledns.packet.*;
import java.util.*;
import java.io.*;
import java.net.*;

public class DNSHandler {

    private DNSClientConnection clientConnection;
    private static int DNS_PORT = 8053;
    private int DNS_SEND_PORT;
    private ArrayList<CIDRData> csvList;

    public DNSHandler(ArrayList<CIDRData> csvList, int sendPort) {
        clientConnection = new DNSClientConnection(DNS_PORT);
        this.csvList = csvList;
        DNS_SEND_PORT = sendPort;
    }

    public void handleDNSRequest(InetAddress dnsServerIP, boolean isRecursive) {
        DatagramPacket dnsPacket = clientConnection.receiveDNSPacket();
        DNS request = DNS.deserialize(dnsPacket.getData(), dnsPacket.getLength());

        if (!isRequestValid(request)) {
            System.out.println("Invalid DNS request");
            return;
        }
        
        

        InetAddress clientIP = dnsPacket.getAddress();
        int clientPort = dnsPacket.getPort();

        DatagramPacket replyPacket;
        DatagramSocket dnsSocket = new DatagramSocket(DNS_PORT);


        // TODO - Handle valid request, recursive or non-recursive
        if (request.isRecursionDesired()) {
            //recursively resolve
            replyPacket = recursivelyResolve(dnsServerIP, dnsPacket, request, dnsSocket);
        } else {
            //non-recursively resolve
            replyPacket = nonRecursivelyResolve(dnsServerIP, dnsPacket, request, dnsSocket);
        }

        dnsSocket.send(replyPacket);
    }

    public DatagramPacket recursivelyResolve(InetAddress rootAddr, DatagramPacket query, DNS req, DatagramSocket socket) {
        
    }

    public DatagramPacket nonRecursivelyResolve(InetAddress rootAddr, DatagramPacket query, DNS req, DatagramSocket socket) {
        byte[] bytes = new byte[4096];

        DatagramPacket newQuery = new DatagramPacket(query.getData(), query.getLength(), rootAddr, DNS_SEND_PORT);
        DatagramPacket recvPacket = new DatagramPacket(bytes, bytes.length);

        socket.send(newQuery);
        socket.receive(recvPacket);

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
