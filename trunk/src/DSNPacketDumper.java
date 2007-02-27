
import dsn.DSNConnector;
import packetparser.PDL;
import packetparser.Parser;
import stream.AbstractSink;
import stream.Scheduler;
import stream.tuple.DSNPacketSource;
import stream.tuple.PacketTuple;


/**
 * Basic example to use the distributed DSN sniffer and the packetparser to decode received packets
 * 
 * @author mringwal
 *
 */
public class DSNPacketDumper {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		// default packet description
		String packetDescription = "packetdefinitions/ewsn07.h";
		// optional description can be given on command line
		if (args.length > 0) {
			packetDescription = args[0];
		}
		// initialise parser from description 
		final PDL parser = Parser.readDescription(packetDescription);
		
		// start DSN sniffer */
		DSNConnector dsnConnection = new DSNConnector();
		dsnConnection.init();
		dsnConnection.connect();
		dsnConnection.setSnifConfig(parser.getSnifferConfig());

		// crate data stream source 
		DSNPacketSource dsnPacketSource = new DSNPacketSource(dsnConnection, parser);

		// create and subscribe data sink
		dsnPacketSource.subscribe( new AbstractSink<PacketTuple>() {
			public void process(PacketTuple o, int srcID, long timestamp) {
				System.out.println( "" + timestamp + " " + o.getDsnNode() + " " + o.toString());
			}
		}, 0);
		
		// start packet reception
		dsnConnection.start();

		// start scheduler
		Scheduler.run( dsnPacketSource );
	}
}
