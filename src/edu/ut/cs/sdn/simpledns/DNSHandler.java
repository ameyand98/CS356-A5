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
            replyPacket = nonRecursivelyResolve(dnsServerIP, dnsPacket);
        }

        DNS reply = DNS.deserialize(replyPacket.getData(), replyPacket.getLength());
        System.out.println("Reply Packet is: " + reply.toString());

        replyPacket.setPort(clientPort);
        replyPacket.setAddress(clientIP);

        clientConnection.sendDNSPacket(replyPacket);
    }

    public DatagramPacket nonRecursivelyResolve(InetAddress rootAddr, DatagramPacket query) throws Exception {
        DatagramPacket recvPacket;
        DatagramPacket newQuery = new DatagramPacket(query.getData(), query.getLength(), rootAddr, DNS_SEND_PORT);

        clientConnection.sendDNSPacket(newQuery);
        recvPacket = clientConnection.receiveDNSPacket();

        return recvPacket;
    }

    // if answer found
    //  if CNAME -> recurse to resolve
    //  else answer found (actually resolve)
    // else answer not found
    //  NS Addr found
    //      Return Packet with new NS addr
    //  NS Addr not found
    //      Return Same Packet (after setting lists)
    public DatagramPacket recursivelyResolve(InetAddress rootAddr, DatagramPacket query, DNS req) throws Exception {
        List<DNSResourceRecord> tgtCNAMEs  = new ArrayList<>();
        List<DNSResourceRecord> tgtAuths  = new ArrayList<>();
        List<DNSResourceRecord> tgtAdds = new ArrayList<>();
        
        boolean isResolved = false;
        DatagramPacket recvPacket = null;
        DatagramPacket newQuery = new DatagramPacket(query.getData(), query.getLength(), rootAddr, DNS_SEND_PORT);

        clientConnection.sendDNSPacket(newQuery);
        while(!isResolved) {

            recvPacket = clientConnection.receiveDNSPacket();
            System.out.println("Packet received from " + recvPacket.getPort());
            DNS recv = DNS.deserialize(recvPacket.getData(), recvPacket.getLength());
            System.out.println("RECEIVED PACKET DESERIALIZED IS: " + recv.toString());

            List<DNSResourceRecord> srcAuths = recv.getAuthorities();
            List<DNSResourceRecord> srcAdds = recv.getAdditional();

            boolean updateAuth = shouldUpdate(srcAuths);
            boolean updateAdd = shouldUpdate(srcAdds);
            System.out.println("Authority/Additional has data: (auth should update)" + updateAuth + " and (additionals should update) " + updateAdd +" additionals are of size " + srcAdds.size() + " and authorities are of size " + srcAuths.size());
            tgtAuths = updateAuth ? srcAuths: tgtAuths;
            tgtAdds = updateAdd ? srcAdds: tgtAdds;

            List<DNSResourceRecord> curAnswers = recv.getAnswers();
            if(curAnswers.size() > 0) {
                //answer found
                DNSResourceRecord ans = curAnswers.get(0);
                if (ans.getType() == DNS.TYPE_CNAME) {
                    System.out.println("CNAME -> resolve through authority section");
                    // CNAME -> resolve through authority section
                    tgtCNAMEs.add(ans);

                    DNS newQueryHeader = buildDNSQueryHeader(req, recv, ans);

                    newQuery = new DatagramPacket(newQueryHeader.serialize(), newQueryHeader.getLength(), rootAddr, DNS_SEND_PORT);
                    clientConnection.sendDNSPacket(newQuery);
                } else {
                    // (BASE CASE) resolved -> build recvPacket
                    System.out.println("RESOLVED -> build recvPacket");
                    isResolved = true;

                    List<DNSResourceRecord> replyAnswers = buildAnswers(curAnswers, tgtCNAMEs, req);

                    // recv.setAuthorities(getEntryList(recv.getAuthorities(), tgtAuths));
                    // recv.setAdditional(getEntryList(recv.getAdditional(), tgtAdds));

                    if(recv.getAuthorities().size() == 0) {
                        recv.setAuthorities(tgtAuths);
                    }
                    if(recv.getAdditional().size() == 0) {
                        recv.setAdditional(tgtAdds);
                    }

                    recv.setAnswers(replyAnswers);
                    recv.setQuestions(req.getQuestions());

                    setFlags(recv, false);

                    recvPacket = new DatagramPacket(recv.serialize(), recv.getLength());
                }
            } else {
                //answer not found
                InetAddress tgtNSAddr = getTgtNSAddr(srcAuths, srcAdds);
                if(tgtNSAddr == null) {
                    System.out.println("NOT RESOLVED JUST EARLY END AS NOTHING FOUND ");
                    //next NS Address not found in additional/authority section -> ignore and just setup reply packet(?)
                    DNS curRecv = new DNS();
                    List<DNSResourceRecord> replyAnswers = buildAnswers(curAnswers, tgtCNAMEs, req);
                    
                    curRecv.setQuestions(recv.getQuestions());
                    curRecv.setAnswers(replyAnswers);
                    
                    curRecv.setAuthorities(getEntryList(recv.getAuthorities(), tgtAuths));
                    curRecv.setAdditional(getEntryList(recv.getAdditional(), tgtAdds));

                    setFlags(curRecv, false);

                    curRecv.setId(req.getId());

                    return new DatagramPacket(curRecv.serialize(), curRecv.getLength());
                } else {
                    newQuery = new DatagramPacket(newQuery.getData(), newQuery.getLength(), tgtNSAddr, DNS_SEND_PORT);
                    clientConnection.sendDNSPacket(newQuery);
                } 
            }

        }

        assert(recvPacket != null);
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

    private InetAddress getTgtNSAddr(List<DNSResourceRecord> srcAuths, List<DNSResourceRecord> srcAdds) {
        for(DNSResourceRecord authEntry: srcAuths) {
            if (authEntry.getType() == DNS.TYPE_NS) {
                String nsNameStr = ((DNSRdataName) authEntry.getData()).getName();
                for(DNSResourceRecord addEntry: srcAdds) {
                    if(authEntry.getType() == DNS.TYPE_NS && addEntry.getType() == DNS.TYPE_A && addEntry.getName().equals(nsNameStr)) {
                        System.out.println("TGT NS FOUND: " + nsNameStr);
                        return ((DNSRdataAddress) addEntry.getData()).getAddress();
                    }
                }
            }
            
        }
        return null;
    }

    private List<DNSResourceRecord> getEntryList(List<DNSResourceRecord> curEntries, List<DNSResourceRecord> tgtEntries) {
        List<DNSResourceRecord> res = new ArrayList<>();
        //Only include A, AAAA, NS, and CNAME entries
        for(DNSResourceRecord entry: curEntries) {
            short curType = entry.getType();
            if (curType == DNS.TYPE_A || curType == DNS.TYPE_AAAA || curType == DNS.TYPE_CNAME || curType == DNS.TYPE_NS) {
                res.add(entry);
            }
        }
        return res.size() == 0 ? tgtEntries : res;
    }

    private List<DNSResourceRecord> buildAnswers(List<DNSResourceRecord> answers, List<DNSResourceRecord> resolvedCNAMEs, DNS req) {
        //HANDLE TXT Records
        if (req.getQuestions().get(0).getType() == DNS.TYPE_A) {
            //TODO: Handle adding TXT records by going through CSV
        }

        for(DNSResourceRecord resolvedCNAME: resolvedCNAMEs) {
            answers.add(0, resolvedCNAME);
        }


        return answers;
    }

    private DNS buildDNSQueryHeader(DNS req, DNS recv, DNSResourceRecord ans) {
        DNS queryHeader = new DNS();

        DNSQuestion question = new DNSQuestion();
        //https://stackoverflow.com/questions/7875513/java-cast-interface-to-class
        DNSRdataName data = (DNSRdataName)ans.getData(); 
        question.setName(data.getName());
        question.setType(recv.getQuestions().get(0).getType());

        queryHeader.setId(req.getId());
        setFlags(queryHeader, true);

        List<DNSQuestion> questions = new ArrayList<>();
        questions.add(question);
        queryHeader.setQuestions(questions);

        return queryHeader;
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

    private boolean shouldUpdate(List<DNSResourceRecord> srcEntries) {
        boolean validTypes = false;
        int authIndex = 0;
        while (!validTypes && authIndex < srcEntries.size()) {
            short curType = srcEntries.get(authIndex++).getType();
            validTypes = (curType == DNS.TYPE_A || curType == DNS.TYPE_AAAA || curType == DNS.TYPE_CNAME || curType == DNS.TYPE_NS);
        }
        return validTypes;
    }


}
