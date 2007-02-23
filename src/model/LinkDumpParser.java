package model;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for LinkDump Logs created by EmStar LinkDump Tool
 * 
 * @author mringwal
 *
 */
public class LinkDumpParser {
	/**
	 * Constructor from BufferedReader
	 */
	private LinkDumpParser( BufferedReader input) {
		reader = input;
	}
	
	/**
	 * Factory method to create a parser which is fed by a String
	 * @param input
	 *
	 */
	public static LinkDumpParser createLinkDumpParserFromString(String input) {
		StringReader stringReader = new StringReader(input);
		BufferedReader buffReader = new BufferedReader( stringReader );
		return new LinkDumpParser(buffReader);
	}

	public static LinkDumpParser createLinkDumpParserFromFile(String fileName) throws FileNotFoundException {
		BufferedReader buffReader = new BufferedReader( new FileReader(fileName) );
		return new LinkDumpParser(buffReader);
	}
	/**
	 * Read an emstar link-dump packet from an input stream
	 * @param
	 * @throws Exception 
	 */
	public Packet readPacket() throws Exception {
		Matcher headerMatcher;
		Matcher receiptMatcher;
		Packet packet = null;
		String lineBuffer;

		// be prepared for some log file header
		while (true) {
			lineBuffer = reader.readLine();
			if (lineBuffer == null) {
				return null;
			}
			headerMatcher = packetHeaderPattern.matcher(lineBuffer);
			if (headerMatcher.matches())
				break;    // found packet header
			receiptMatcher = packetReceiptPattern.matcher(lineBuffer);
			if (receiptMatcher.matches())
				continue;    // try again
			if (logFileHeader)
				continue; // try again
			// throw new Exception("Packet Parser: Header incorret. \n"
			//		+ lineBuffer); // error
			return null;
		}
		logFileHeader = false;
		packet = new Packet();
		packet.bad = false;
		if (headerMatcher.group(1) != null) {
			packet.bad = headerMatcher.group(1).equalsIgnoreCase("ERR");
		}
		packet.src = headerMatcher.group(2);
		packet.dst = headerMatcher.group(3);
		packet.type = Integer.parseInt(headerMatcher.group(4));
		packet.group = Integer.parseInt(headerMatcher.group(5));
		packet.data_len = Integer.parseInt(headerMatcher.group(6));
		//			for (int i = 0; i <= headerMatcher.groupCount(); i++) {
		//				System.out.println("group "+i+" = " + headerMatcher.group(i));
		//			} 
		if (headerMatcher.group(8) != null) {
			packet.rssi = Integer.parseInt(headerMatcher.group(8));
		} else {
			packet.rssi = -1;
		}
		// store time given as seconds + useconds as ms
		packet.time_ms = Integer.parseInt(headerMatcher.group(9)) * 1000L
				+ Integer.parseInt(headerMatcher.group(10)) / 1000;
		// allocate buffer
		packet.data = new int[packet.data_len];
		int offset = 0;
		// awaiting data

		while (offset < packet.data_len) {
			lineBuffer = reader.readLine();
			Matcher lineMatcher = dataLinePattern.matcher(lineBuffer);
			if (lineMatcher.matches()) {
				int readOffset = Integer.parseInt(lineMatcher.group(1));
				if (readOffset != offset)
					throw new Exception(
							"Packet Parser: DataLine Offset incorrect. is: "
									+ readOffset + " should be: " + offset);
				StringTokenizer tokenizer = new StringTokenizer(lineMatcher
						.group(2));
				while (tokenizer.hasMoreTokens()) {
					packet.data[offset++] = Integer.parseInt(tokenizer
							.nextToken(), 16);
				}
			}
		}
		return packet;
	}

	 
	/** 
	 * Test Packet parsing
	 * @throws IOException 
	 */
	public static boolean testParsing() throws IOException{
		String testPacket = "" +
"    src=0.0.0.2 dst=255.255.255.255 type=TOS[1,125] data_len=4 rssi=100 time=1145788062.264803\n" +
"        0000: 02 00 70 00                                      ..p.\n"  +
"Pr  src=0.0.0.1 dst=0.0.0.2 type=TOS[4,125] data_len=25 rssi=26 time=1145788062.652498 \n" +
"        0000: 01 00 FF FF 01 00 06 01 05 02 00 02 00 59 02 02  .............Y..\n" +
"        0016: 00 72 3E 21 03 00 6C 80 1A                       .r>!..l..\n" +
"Pr  src=0.0.0.1 dst=0.0.0.2 type=TOS[4,125] data_len=25 rssi=41 time=1145788062.673750 \n" +
"        0000: 01 00 FF FF 01 00 06 01 05 02 00 02 00 59 02 02  .............Y..\n" +
"        0016: 00 72 3E 21 03 00 6C 80 1A                       .r>!..l..\n" + 
">>  src=0.0.0.2 dst=255.255.255.255 type=TOS[1,125] data_len=4 rssi=1 time=1151942530.579073\n" + 
"        0000: 02 00 FF 00                                      ....\n"+
">>  src=0.0.0.2 dst=255.255.255.255 type=MAC[RECEIPT] data_len=0 rssi=1 time=1151942530.579073\n";
		
		LinkDumpParser parser = createLinkDumpParserFromString( testPacket );
		
		try {
			System.out.println( parser.readPacket() );
			System.out.println( parser.readPacket() );
			System.out.println( parser.readPacket() );
			System.out.println( parser.readPacket() );
			System.out.println( parser.readPacket() );
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length == 0 ) {
		LinkDumpParser.testParsing();
		} else {
			LinkDumpParser parser= createLinkDumpParserFromFile(args[0]);
			Packet packet;
			while (true) {
				packet = parser.readPacket();
				if (packet == null) {
					return;
				}
				System.out.println( packet );
			}
		}
	}

	/** patterns for line matching/parsing */
	Pattern packetHeaderPattern = Pattern.compile("^(Pr|ERR|\\>\\>)?\\s++src=(\\d++\\.\\d++\\.\\d++\\.\\d++)"+
			"\\sdst=(\\d++\\.\\d++\\.\\d++\\.\\d++)\\s++type=TOS\\[(\\d++),(\\d++)\\]\\s++" +
			"data_len=(\\d++)\\s++(rssi=(\\d++)\\s++)?time=(\\d++)\\.(\\d++)\\s*$");
	Pattern packetReceiptPattern = Pattern.compile("^\\>\\>.*RECEIPT.*$");
	Pattern dataLinePattern = Pattern.compile("^.*\\s++(\\d++):\\s((\\w++\\s)++)\\s.*$");

	/** private members */
	private BufferedReader reader = null;
	private boolean logFileHeader = true;
}
