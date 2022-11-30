package edu.ut.cs.sdn.simpledns;
import java.net.*;
import java.io.IOException;
import java.io.*;
import java.util.*;
import java.nio.ByteBuffer;

public class SimpleDNS 
{

	private ArrayList<CIDRData> csvList;

	public static void main(String[] args) {
        System.out.println("Hello, DNS!"); 

		if (!validArguments(args)) {
			System.out.println("Invalid arguments!");
			return;
		}

		InetAddress rootDNSip;
		// first element is root ip, second element is csv file
		String[] csvAndRootIp = parseArgs(args);

		try {
			rootDNSip = InetAddress.getByName(csvAndRootIp[0]);
		} catch (UnknownHostException e) {
			System.out.println("Invalid root IP");
			return;
		}

		try {
			parseCSV(csvAndRootIp[1]);
		} catch (IllegalArgumentException e) {
			System.out.println("Cannot parse csv file");
			return;
		}

		// resolve dns request
		DNSHandler requestHandler = new DNSHandler(csvList);
		while(true) {
			requestHandler.handleDNSRequest(rootDNSip);
		}
		
	}

	private static boolean validArguments(String[] args) {
		return (args.length == 4 &&
            	((args[0].equals("-r") && args[2].equals("-e")) ||
                (args[2].equals("-r") && args[0].equals("-e"))));
	}

	// First element is root ip string 
	// Second element is csv file
	private static String[] parseArgs(Strin[] args) {
		String[] argVals = new String[2];
		argVals[0] = args[0].equals("-r") ? args[1] : args[3];
		argVals[1] = args[0].equals("-e") ? args[1] : args[3];
	}

	private void parseCSV(String csvFile) {
		csvList = new ArrayList<>();
		FileReader file = new FileReader(csvFile);
		BufferedReader reader = new BufferedReader(file);

		String currLine;
		while ((currLine == reader.readLine()) != null) {
			currLine = currLine.trim();
			if (!currLine.isEmpty()) {
				CIDRData record = parseCSVLineToCIDRData(currLine);
				this.csvList.add(record);
			}
		}
	}

	private CIDRData parseCSVLineToCIDRData(String record) throws UnknownHostException {
		String[] recordData = record.split("[/,]");

		InetAddress netAddrObj = InetAddress.getByName(recordData[0]);
		ByteBuffer buff = ByteBuffer.wrap(netAddrObj.getAddress());
		int networkAddr = buff.getInt();
		int subnetMask = (0xffffffff) << (Integer.SIZE - Integer.parseInt(recordData[1]));

		CIDRData ec2Record = new CIDRData(networkAddr, subnetMask, recordData[2]);
		return ec2Record;

	}
}
