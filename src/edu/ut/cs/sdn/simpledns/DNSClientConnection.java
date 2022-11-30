package edu.ut.cs.sdn.simpledns;
import edu.ut.cs.sdn.simpledns.packet.*;
import java.net.*;
import java.io.*;

public class DNSClientConnection {

    private DatagramSocket connection;

    public DNSClientConnection(int port) throws Exception {
        try {
            connection = new DatagramSocket(port);
        } catch (Exception e) {
            System.out.println("Exception: ");
            System.out.println(e);
        }
        
    }

    public DatagramPacket receiveDNSPacket() throws IOException {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        connection.receive(packet);
        return packet;
    }

    public void sendDNSPacket(DNS header, InetAddress ip, int port) throws IOException {
        byte[] buffer = header.serialize();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, ip, port);
        connection.send(packet);
    }

    public void closeConnection() {
        connection.close();
    }


}
