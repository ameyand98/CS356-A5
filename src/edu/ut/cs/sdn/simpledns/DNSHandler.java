package edu.ut.cs.sdn.simpledns;
import edu.ut.cs.sdn.simpledns.packet.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class DNSHandler {

    private DNSClientConnection clientConnection;
    private InetAddress ROOT_DNS_ADDR;
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

        ROOT_DNS_ADDR = dnsServerIP;

        if (!isRequestValid(request)) {
            System.out.println("Invalid DNS request");
            return;
        }
        
        System.out.println("Valid Request Received");
        
        InetAddress clientIP = dnsPacket.getAddress();
        int clientPort = dnsPacket.getPort();

        DatagramPacket replyPacket;
        DNS reply;

        System.out.println("IP, Port, and Socket created");


        // TODO - Handle valid request, recursive or non-recursive
        if (request.isRecursionDesired()) {
            //recursively resolve
            System.out.println("Recursively Resolved");

            reply = recursivelyResolve(dnsServerIP, request, 0);
            if (reply != null) {
                replyPacket = new DatagramPacket(reply.serialize(), reply.getLength(), clientIP, clientPort);

                clientConnection.sendDNSPacket(replyPacket);
            }
        } else {
            //non-recursively resolve
            System.out.println("Non-recursively Resolved");
            reply = nonRecursivelyResolve(dnsServerIP, dnsPacket);
            System.out.println("Reply Packet is: " + reply.toString());
            if (reply != null) {
                replyPacket = new DatagramPacket(reply.serialize(), reply.getLength(), clientIP, clientPort);

                clientConnection.sendDNSPacket(replyPacket);
            }
        }
        
        

        
    }

    public DNS nonRecursivelyResolve(InetAddress rootAddr, DatagramPacket query) throws Exception {
        DatagramPacket recvPacket;
        DatagramPacket newQuery = new DatagramPacket(query.getData(), query.getLength(), rootAddr, DNS_SEND_PORT);

        clientConnection.sendDNSPacket(newQuery);
        recvPacket = clientConnection.receiveDNSPacket();
        DNS recv = DNS.deserialize(recvPacket.getData(), recvPacket.getLength());
        DNS req = DNS.deserialize(newQuery.getData(), newQuery.getLength());

        if (req.getQuestions().get(0).getType() == DNS.TYPE_A) {
            handleTxtRecords(recv.getAnswers());
        }

        return recv;
    }


    public DNS recursivelyResolve(InetAddress destAddr, DNS req, int depth) throws Exception {
        DatagramPacket newQuery = new DatagramPacket(req.serialize(), req.getLength(), destAddr, DNS_SEND_PORT);
        DatagramPacket recvPacket;
        DNS recv = null;

        clientConnection.sendDNSPacket(newQuery);
        recvPacket = clientConnection.receiveDNSPacket();
        System.out.println("Entered depth " + depth + " and sent query to " + destAddr.toString() + " with the following request: " + req.toString());
        recv = DNS.deserialize(recvPacket.getData(), recvPacket.getLength());
        System.out.println("Successfully received packet from " + recvPacket.getAddress() + " with " + recv.toString());

        List<DNSResourceRecord> auths = recv.getAuthorities();
        List<DNSResourceRecord> adds = recv.getAdditional();
        List<DNSResourceRecord> answers = recv.getAnswers();

        DNSQuestion curQuestion = req.getQuestions().get(0);

        if(answers.size() > 0) {
            //Answers found
            DNSResourceRecord ans = answers.get(0);
            if (ans.getType() == curQuestion.getType()) {
                System.out.println("Answer and Question matched -> build reply packet and return");
                setFlags(recv, false);
                if (curQuestion.getType() == DNS.TYPE_A) {
                    handleTxtRecords(recv.getAnswers());
                }
                return recv;
            } else {
                System.out.println("Received answer of type " + ans.getType() + " with question type of " + curQuestion.getType()  + ". CNAME Resolution case");
                //
                DNS newQueryHeader = buildDNSQueryHeader(req, ans.getData().toString(), curQuestion);
                
                recv = recursivelyResolve(ROOT_DNS_ADDR, newQueryHeader, depth + 1);
                recv.getAnswers().add(ans);
                recv.getQuestions().get(0).setName(curQuestion.getName());
                return recv;
            }
        } else {
            //Answers not found
            boolean matchFound = false;
            for(DNSResourceRecord authEntry: auths) {
                if (authEntry.getType() == DNS.TYPE_NS) {
                    String nsNameStr = ((DNSRdataName) authEntry.getData()).getName();
                    for(DNSResourceRecord addEntry: adds) {
                        if(authEntry.getType() == DNS.TYPE_NS && addEntry.getType() == DNS.TYPE_A && addEntry.getName().equals(nsNameStr)) {
                            
                            matchFound = true;
                            InetAddress tgtNSAddr = ((DNSRdataAddress) addEntry.getData()).getAddress();
                            System.out.println("A match has been found for Name Server " + nsNameStr + " at the address " + tgtNSAddr.toString());
                            recv = recursivelyResolve(tgtNSAddr, req, depth + 1);
                            if (recv != null && recv.getAnswers().size() > 0) {
                                recv.getAuthorities().addAll(auths);
                                recv.getAdditional().addAll(adds);
                                return recv;
                            }
                        }
                    }
                    if (!matchFound) {
                        System.out.println("There were no matches, try recursing for each authority");

                        DNS newQueryHeader = buildDNSQueryHeader(req, nsNameStr, curQuestion);

                        recv = recursivelyResolve(ROOT_DNS_ADDR, newQueryHeader, depth + 1);
                        if (recv != null) {
                            recv.getQuestions().get(0).setName(curQuestion.getName());
                            return recv;
                        }
                    }
                }
            }
        }
        return recv;
    }

    private DNS buildDNSQueryHeader(DNS req, String name, DNSQuestion curQuestion) {
        DNS newQueryHeader = DNS.deserialize(req.serialize(), req.getLength());
        newQueryHeader.setQuestions(new ArrayList<DNSQuestion>());
        DNSQuestion question = new DNSQuestion(name, curQuestion.getType());
        newQueryHeader.getQuestions().add(question);
        return newQueryHeader;
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
        // System.out.println("Valid Q: " + validQuestions + " Valid OP: " + (request.getOpcode() == 0) + " Valid Type: " + request.isQuery());
        return (validQuestions && request.getOpcode() == 0 && request.isQuery());
    }


    private void handleTxtRecords(List<DNSResourceRecord> answers) {
        for(int i = answers.size() - 1; i >= 0; i--) {
            DNSResourceRecord ans = answers.get(i);
            if(ans.getType() == DNS.TYPE_A) {
                DNSRdataAddress ansAddr = (DNSRdataAddress)ans.getData();
                CIDRData tgt = isAssociated(ansAddr.getAddress());
                if (tgt != null) {
                    DNSRdataString formattedEntry = new DNSRdataString(tgt.getRegion() + "-" + ansAddr.toString());

					DNSResourceRecord txtRecord = new DNSResourceRecord();
					txtRecord.setType(DNS.TYPE_TXT);
					txtRecord.setTtl(3600);
					txtRecord.setName(ans.getName());
					txtRecord.setData(formattedEntry);

					answers.add(txtRecord);
                }
            }
        }
    }

    private CIDRData isAssociated(InetAddress ansAddr) {
        int ansAddrIp = ipToInt(ansAddr);
		for(CIDRData entry: csvList) {
			int base = entry.getNetworkAddress();
			int pow = 32 - entry.size();
			int power = (int) Math.pow(2.0, pow);
			if(ansAddrIp >= base && ansAddrIp <= (base + power))
			{
				return entry;
			}

		}

		return null;
    }

    // Conversion code from this source
    //https://stackoverflow.com/questions/10087800/convert-a-java-net-inetaddress-to-a-long
    private int ipToInt(InetAddress tgt) {
        // ByteOrder.BIG_ENDIAN by default
        ByteBuffer buffer = ByteBuffer.allocate(Integer.SIZE);
        buffer.put(tgt.getAddress());
        buffer.position(0);
        int intValue = buffer.getInt();
        return intValue;
    }

    
    private void setFlags(DNS header, boolean isQuery) {
        header.setQuery(isQuery);
        header.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        header.setTruncated(false);
        header.setRecursionDesired(false);
        header.setAuthenicated(false);

        if (!isQuery) {
            header.setAuthoritative(false);
            header.setRecursionAvailable(false);
            header.setCheckingDisabled(false);
            header.setRcode(DNS.RCODE_NO_ERROR);
        }
    }
}
