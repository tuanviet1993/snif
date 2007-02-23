package model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PacketSorter processes incoming Packets from several LinkDumpParsers and 
 * sorts them according to their received time. In addtion, it does filter out
 * duplicate packets that are recorded in different logs but with a almost identical timestamp.
 * 
 * It also uses the time of the very first packet to establish a reference time base
 * 
 * @author mringwal
 *
 */

public class PacketSorter {

	/**
	 *  Construct PacketSorter Object to get all packets contained in a specific directory
	 * 
	 * @param path
	 * @throws Exception
	 */
	public PacketSorter(String path) throws Exception {
		parsers = new ArrayList<LinkDumpParser>();
		getParsers(parsers, new File(path));
		packets = new Packet[parsers.size()];
		lastPackets = new Packet[parsers.size()];
		// get first packet of each parser
		int i;
		for (i = 0; i < parsers.size(); i++) {
			packets[i] = parsers.get(i).readPacket();
		}
		// set time reference
		time_base = getTimeBaseFromSARS(path);
	}

	/**
	 * Parse simulation start time from sars.log file
     *
    	 * @return Simulation start time in ms
	 * @throws Exception 
	 */
	long getTimeBaseFromSARS(String path) throws Exception{
		String fileName = path + "/sars.log";
		BufferedReader buffReader = null;
		long timeBase = 0;
		try {
			buffReader = new BufferedReader( new FileReader(fileName) );
			buffReader.readLine();
			String timeLine = buffReader.readLine();
			Matcher matcher = baseTimeFilter.matcher( timeLine );
			if (matcher.matches()){
				String timeString = matcher.group(1);
				SimpleDateFormat dateParser = new SimpleDateFormat( "EEE MMM d HH:mm:ss zzz yyyy");
				Date time = dateParser.parse( timeString);
				timeBase = time.getTime();
				// System.out.println( "Simulation started "+time + " .. time = " +timeBase);
			} else { 
				throw new Exception ( "Error: could not extract base time from: \n" + timeLine);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			if (buffReader != null)
				buffReader.close();
		}
		return timeBase;
	}
	/**
	 * get list of initialized LinkDumpParsers by using all matching log files in directory
	 * 
	 * @param list of LinkDumpParsers created
	 * @param d directory to scan for log files
	 */
	public static void getParsers(ArrayList<LinkDumpParser> list, File d) {
		if (!d.exists()) return;
		
		File[] children = d.listFiles();
		for (File f : children) {
			Matcher matcher = logFilter.matcher(f.getName());
			if (matcher.matches()) {

				// filter dsn nodes
				String dsnNode = matcher.group(1);
				if (dsnNode == null) {
					dsnNode = matcher.group(2);
				}
				int dsnNodeID = Integer.parseInt( dsnNode );
				boolean use = true;
				if (dsnNodes != null) {
					use = false;
					for (int i=0;i<dsnNodes.length;i++) {
						if (dsnNodeID==dsnNodes[i]) {
							use = true;
						}
					}
				}
				if (!use) continue;
				
				LinkDumpParser parser;
				try {
					parser = LinkDumpParser.createLinkDumpParserFromFile(f
							.getAbsolutePath());
					// System.out.println("Found.. " + f.getName());
					list.add(parser);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Get packet with minimal timestamp from all ready LinkDumpParsers
	 * 
	 * @return minimal packet
	 * @throws Exception
	 */
	public Packet getNextPacket() throws Exception {
		int minIndex = getEarliestPacktIdx();
		if (minIndex < 0)
			return null;
		// refer to found packet
		Packet packet = packets[minIndex];
		packets[minIndex] = parsers.get(minIndex).readPacket();
		// update packet clock
		return packet;
	}

	/**
	 * Get packet with minimal timestamp from all ready LinkDumpParsers and
	 * ignore dupliacate packets within duplicate_timeout
	 *
	 * @return packet
	 */
	public Packet getNextUniquePacket() {
		try {

			Packet packet = null;
			while (packet == null ) {
				packet = getNextPacket();
				if (packet == null)
					return null;
				// compare with last packets
				boolean dup = false;
				for (int i = 0; i < parsers.size() && !dup; i++) {
					if (lastPackets[i] != null && lastPackets[i].equals(packet)) {
						long delta_t = Math.abs(packet.time_ms - lastPackets[i].time_ms);
						if (delta_t < duplicate_timeout) {
							packet = null;
							dup = true;
						}
					}
				}
			}

			// cache packet for later comparison
			for (int i = 0; i < parsers.size() - 1; i++) {
				lastPackets[i] = lastPackets[i + 1];
			}
			lastPackets[parsers.size() - 1] = packet;
			return packet;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @return
	 */
	private int getEarliestPacktIdx() {
		int minIndex = 0;
		boolean found = false;
		// get minimum time
		for (int i = 0; i < parsers.size(); i++) {
			if (packets[i] != null
					&& (found == false || packets[i].time_ms < packets[minIndex].time_ms)) {
				minIndex = i;
				found = true;
			}
		}
		if (found)
			return minIndex;
		return -1;
	}

	/** 
	 * Get Duplicate Packet threshold timeout
	 * 
	 * @return time in milliseconds
	 */
	public int getDuplicateTimeout() {
		return duplicate_timeout;
	}

	/** 
	 * Set Duplicate Packet threshold timeout
	 * 
	 * @param time in milliseconds
	 */
	public void setDuplicateTimeout(int duplicate_timeout) {
		this.duplicate_timeout = duplicate_timeout;
	}

	/** 
	 * Get time of first packet received
	 * 
	 * @return time in ms
	 */
	public long getTimeBase() {
		return time_base;
	}

	/** 
	 * Get time simulation start time
	 * 
	 * @return time in ms
	 */
	public static long getCurrentTime() {
		return 0;
	}

	/**
	 * Get simulation running time 
	 * @return time in seconds
	 */
	public static long getSimulationTime() {
		return 0;
	}

	/**
	 * get number of packet logs
	 * 
	 * @return
	 */
	public int getParserCount() {
		return parsers.size();
	}

	
	public static void postEvent(Event event) {
		events.add(event);
	}

	
	/** constants */
	public static final long epoch = 540 * 1000;

	/** list of individual parsers */
	private ArrayList<LinkDumpParser> parsers;

	/** current packet of individual parsers */
	private Packet packets[];

	/** Timeout to detect duplicate packets */
	private int duplicate_timeout = 5;

	/** Simulation start time im ms */
	private long time_base;

	/** Cache to detect duplicate packets */
	public Packet lastPackets[];


	/** FILTER -- if not null, only use those nodes as DSN nodes */
	public static int dsnNodes[] = null;

	/** pattern to identify LinkDump Logs*/
	static Pattern logFilter = Pattern.compile("DSNlog(.*)\\.txt||DSNlog(.*)\\.log");

	/** pattern to get information [nodeId,time] of injected node crash */
	static Pattern pathPattern = Pattern.compile(".*group\\d++\\.\\d++traffic\\d++\\.(\\d++)die(\\d++).*");
	
	/** The time is the last entry in the second line
	 * # (/home/mringwal/.sympathy_3/group1.1000die27.600traffic30.epoch3.iter1a.sim) Wed May 17 02:12:29 CEST 2006 */
	static Pattern baseTimeFilter = Pattern.compile(".*\\(.*\\)\\s*(.*)");
	
	/** Event Queue */
	static LinkedList<Event> events = new LinkedList<Event>();
}
