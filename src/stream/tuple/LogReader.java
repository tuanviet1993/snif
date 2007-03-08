package stream.tuple;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.util.StringTokenizer;

import packetparser.DecodedPacket;
import packetparser.PDL;
import stream.AbstractSource;

public class LogReader extends AbstractSource<PacketTuple> {

	private class Packet {
		long timestamp;
		String dsnNode;
		String typeString;
		byte data[];
		int len;
	}
	
	/** private members */
	private BufferedReader reader = null;
	private PDL parser;
	
	/**
	 * Constructor from BufferedReader
	 */
	private LogReader( BufferedReader input) {
		reader = input;
	}

	public void setParser( PDL parser) {
		this.parser = parser;
		
	}
	@Override
	public PacketTuple next() {
		Packet packet = null;
		try {
			packet = readPacket();
		} catch (Exception e) {
		}
		if (packet == null) return null;
		byte[] rawData = new byte[packet.len];
		System.arraycopy(packet.data, 0, rawData, 0, packet.len);
		PacketTuple packetTuple = new PacketTuple( DecodedPacket.createPacketFromBuffer(parser, rawData), packet.timestamp);
		packetTuple.setDsnNode(packet.dsnNode);
		return packetTuple;
	}

	/**
	 * Factory method to create a parser which is fed by a String
	 * @param input
	 *
	 */
	public static LogReader createLogReaderFromString(String input) {
		StringReader stringReader = new StringReader(input);
		BufferedReader buffReader = new BufferedReader( stringReader );
		return new LogReader(buffReader);
	}

	public static LogReader createLogReaderFromFile(String fileName) throws FileNotFoundException {
		BufferedReader buffReader = new BufferedReader( new FileReader(fileName) );
		return new LogReader(buffReader);
	}
	
	/**
	 * Read an emstar link-dump packet from an input stream
	 * @param
	 * @throws Exception 
	 */
	public Packet readPacket() throws Exception {
		String lineBuffer;
		Packet packet = new Packet();
		
		// read header
		lineBuffer = reader.readLine();		
		StringTokenizer tokenizer = new StringTokenizer(lineBuffer);
		packet.timestamp = Integer.parseInt( tokenizer.nextToken());
		packet.dsnNode = tokenizer.nextToken();
		packet.typeString = tokenizer.nextToken();
		
		// read data
		int offset = 0;
		packet.data = new byte[255];
		while (true) {
			lineBuffer = reader.readLine();
			if (lineBuffer.length() == 0) {
				break;
			}
			tokenizer = new StringTokenizer(lineBuffer);
			// ignore address
			tokenizer.nextToken();
			while (tokenizer.hasMoreTokens()) {
				packet.data[offset++] = (byte) Integer.parseInt(tokenizer.nextToken(), 16);
			}
		};
		packet.len = offset;
		return packet;
	}
}