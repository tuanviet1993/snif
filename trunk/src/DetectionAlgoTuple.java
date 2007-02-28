import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import model.NodeAddress;
import model.PacketSorter;

import packetparser.PDL;
import packetparser.PacketTemplate;
import packetparser.Parser;
import stream.*;
import stream.tuple.*;
import stream.tuple.DistinctInWindow;

/**
 * Detection Algo Implementation based on generic tuple processing
 * 
 * TODO ++ add latencyObservator : comparing latency to max latency
 * 
 * TODO add PacketType attribute to PacketTuple
 * TODO model.*: clean up old detection model 
 * 
 * @author mringwal
 *
 */

public class DetectionAlgoTuple {
	public static final int WORD_MAX_VALUE = 65535;

	static HashSet<Object> visited = new HashSet<Object>();

	static int totalData;

	private static int packetMultiply;
	
	// total bandwith aggregator
	AbstractSink<Tuple> totalDataAggregator = new AbstractSink<Tuple>() {
		public void process(Tuple o, int srcID, long timestamp) {
			totalData += ((PacketTuple) o).getIntAttribute("TOSmsg.length") + 7; // addr(2)+type+grp+crc(2)
		}
	};

	
	/**
	 * Dump packets of path
	 * 
	 * @param args Path to folder containing logFilter logs
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		String path;

		// init
		final PDL parser = Parser.readDescription("packetdefinitions/tosmsg.h");
		registerTuples();
		registerPackets(parser);

		// use only limited set of dsn nodes
		PacketSorter.dsnNodes = new int[] { 31, 33, 35 };
		
		// simulate different packet loss variants
		// double packetLossVariants [] = new double [] { 0.0, 0.1, 0.2, 0.3 }; s
		// double packetLossVariants [] = new double [] { 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6 };
		// double packetLossVariants [] = new double [] { 0.4, 0.5, 0.6 };
		double packetLossVariants [] = new double [] { 0.3 };

		// simulate different ns
		// int   packetMultiply [] = new int [] { 2, 3, 4, 5, 6, 7, 8, 10};
		int   packetMultiply [] = new int [] { 3,  4, 5, 6, 7, 8, 9, 10 };
		// int   packetMultiply [] = new int [] { 9, 10 };
		// int packetMultiply [] = new int [] { 8 };
		
		// get experiment(s)

		// devel
		// block -> get rid of routing loop reports, P=4
		// path = "/Volumes/MRINGWAL/Simulations/dsn-block01/group3.600traffic30.1060block27.epoch3.iter1a.sim/";
		// network partition
		// path = "/Volumes/MRINGWAL/Simulations/dsn-partition11-4to16/group4.600traffic30.825partition7.epoch3.iter1a.sim";
		
		// evaluation
		String basePath = "/Projects/Simulations/";
		// path = basePath + "dsn-07"; 					// single node crash experiment, p=6
		// path = basePath + "dsn-block01/"; 			// blocking experiment, p=6
		// path = basePath + "dsn-block02/"; 			// blocking experiment, p=4
		// path = "dsn-ok01/"; 				// no crash, p=1-6
		// path = basePath + "dsn-ok02-newPositions/";	// no crash, p=4, new positions
		// path = "/Volumes/MRINGWAL/Simulations/dsn-partition11-4to16/";	// network partition, p=4, 4-16 crashed
		// path = basePath + "dsn-partition12/";		// network partition, p=4, 4-16 crashed
		path = basePath + "dsn-partition16/";		// network partition 13, p=4, 4-16 crashed
		
		args = new String[] {  path };
		
		boolean foundDSNFolder = false;
		if (args.length > 0) {
			File dir = new File(args[args.length-1]);
			if (dir.exists()) {
				File[] children = dir.listFiles();
				if (children != null) {
					for (File f : children) {
						// System.out.println("Testing.." + f);
						if (dsnFilter.matcher(f.getName()).matches()) {
							// System.out.println("Matched.." + f);
							foundDSNFolder = true;
							// processing all sub-folders
							args[0] = f.getAbsolutePath();
							processExperiment( parser, f.getAbsolutePath(), packetLossVariants, packetMultiply);
							System.out.println();
						}
					}
				}
				if (!foundDSNFolder)
				processExperiment(parser, path, packetLossVariants, packetMultiply);
			}
		}
	}
	

	private static void processExperiment(final PDL parser, String path, double packetLossVariants[], int packetMultiplyVariant[] ) throws Exception {
		for (int i=0;i<packetLossVariants.length;i++) {
			for (int j=0; j<packetMultiplyVariant.length; j++) {
				Scheduler.packetloss = (float) packetLossVariants[i];
				packetMultiply = packetMultiplyVariant[j]; 
				processExperiment(parser, path, "dsnLog-"+packetLossVariants[i]+"-"+ packetMultiply+".log");
			}
		}
	}
	
	private static void logLine( FileWriter writer, String text)  {
		System.out.println( text );
		try {
			writer.write(text+"\n");
		} catch (IOException e) { /** */}
	}
	/**
	 * @param parser
	 * @param path
	 * @param logName TODO
	 * @throws IOException
	 * @throws Exception
	 */
	private static void processExperiment(final PDL parser, String path, String logName) throws IOException, Exception {

		final int beaconPeriod  = 10 * 1000;
		final int linkAdvPeriod = 80 * 1000;
		final int pathAdvPeriod = 80 * 1000;
		@SuppressWarnings("unused")
		final int dataPeriod    = 30 * 1000;
		
		
		// run gc and show heap
		System.gc();
		System.out.println("Free Memory: " + Runtime.getRuntime().freeMemory());
		
		String dsnResultPath = path + "/"+logName;
		final FileWriter dsnLogWriter = new FileWriter(dsnResultPath);

		logLine( dsnLogWriter, "dsnSympathy. Processing "+path +"to "+logName);
		
		int id = 1;
		
		totalData = 0;
		
		// address of sink in observed network
		final NodeAddress theSink = new NodeAddress( 2 );
		
		// packet source
		PacketMerger merger = new PacketMerger( new PacketSorter(path), parser );
		
		// filter packets with identical content reported by different DSN nodes within short time (5 ms)
		DistinctInWindow distinctInWindow = new DistinctInWindow(5);
		Filter<PacketTuple> dupFilter = new Filter<PacketTuple>(distinctInWindow);
		merger.subscribe(dupFilter, id++);

		// get linkBeacon tuple stream
		Filter<Tuple> linkBeaconFilter = new Filter<Tuple>(
				new AttributePredicate("TOS_Msg.type", parser.getValue( "MULTIHOP_LINKESTIMATORBEACON")));
		dupFilter.subscribe( linkBeaconFilter, id++);
			
		// extract layer 2 source address from beacons
		Mapper linkBeaconIDMapper = new Mapper( "IDTuple", "LinkEstimatorBeacon.id", "nodeID");
		linkBeaconFilter.subscribe( linkBeaconIDMapper, id++);
		
		// extract layer 2 source address and seqNr from beacons
		Mapper seqNrMapper = new Mapper( "SeqNrTuple", "LinkEstimatorBeacon.id", "nodeID", "LinkEstimatorBeacon.seqNr", "seqNr");
		linkBeaconFilter.subscribe( seqNrMapper, id++);

		// get overall bservation quality
		TupleGroupAggregator totalObservationQuality = new TupleGroupAggregator( new Ratio ( "ObservationQuality", "seqNr"), "nodeID");
		seqNrMapper.subscribe( totalObservationQuality, id++ );

		// check for seq nr reset on beacon seq nr
		SeqNrResetDetector seqResetDetector = new SeqNrResetDetector("LinkEstimatorBeacon.id",
				"LinkEstimatorBeacon.seqNr", WORD_MAX_VALUE, 10 );
		linkBeaconFilter.subscribe(seqResetDetector, id++ );

		// get linkAdvertisement tuple stream
		Filter<Tuple> linkAdvertisementFilter = new Filter<Tuple>(
				new AttributePredicate("TOS_Msg.type", parser.getValue("MULTIHOP_LINKESTIMATORADVERTISEMENT")) ); 
		dupFilter.subscribe( linkAdvertisementFilter, id++);
				
		// extract layer 2 source address from beacons
		Mapper linkAdvertisementIDMapper = new Mapper( "IDTuple", "LinkAdvertisement.id", "nodeID");
		linkAdvertisementFilter.subscribe( linkAdvertisementIDMapper, id++);
		
		// translate LinkAdvertisementBeacons into Neighbour sightings
		ArrayExtractor linkAdvertisementExtractor = new ArrayExtractor( "LinkQuality",
				"LinkAdvertisement.links.length", "LinkAdvertisement.links");
		linkAdvertisementFilter.subscribe( linkAdvertisementExtractor, id++);

		// extract layer 2 source address from beacons
		Mapper linkAdvertisementMapper = new Mapper( "NodeSeen" , "LinkAdvertisement.id", "reportingNode", "id", "seenNode");
		linkAdvertisementExtractor.subscribe( linkAdvertisementMapper, id++);
		
		// get pathAdvertisementFilter tuple stream
		Filter<Tuple> pathAdvertisementFilter = new Filter<Tuple>(
		new AttributePredicate("TOS_Msg.type", parser.getValue("MULTIHOP_PATHADVERTISEMENT"))); 
		dupFilter.subscribe( pathAdvertisementFilter, id++);
		
		// extract layer 2 source address from beacons
		Mapper pathAdvertisementIDMapper = new Mapper( "IDTuple", "PathAdvertisement.id", "nodeID");
		pathAdvertisementFilter.subscribe( pathAdvertisementIDMapper, id++);

		// extract path quality
		ArrayExtractor pathAdvertisementExtractor = new ArrayExtractor( "PathQuality",
				"PathAdvertisement.paths.length", "PathAdvertisement.paths");
		pathAdvertisementFilter.subscribe( pathAdvertisementExtractor, id++);

		// extract src source address from traced multihop packets
		Mapper pathAdvertisementMapper = new Mapper( "PathAnnouncement" , "PathAdvertisement.id", "nodeID", "quality", "quality");
		pathAdvertisementExtractor.subscribe( pathAdvertisementMapper, id++);
		
		// get multiHopPacket stream
		Filter<Tuple> multiHopFilter = new Filter<Tuple>(
				new AttributePredicate("TOS_Msg.type", parser.getValue("MULTIHOP_MULTIHOPPACKET")) ); 
		dupFilter.subscribe( multiHopFilter, id++);
		
		// PacketTracer reports last node sending a packet before
		PacketTupleTracer packetTracer = new PacketTupleTracer("PacketTracerTuple", "TOS_Msg.addr", "MultiHopPacket.src", "MultiHopPacket.dst", "MultiHopPacket.seqno");
		multiHopFilter.subscribe( packetTracer, id++);

		// extract src source address from traced multihop packets
		Mapper multihopIDMapper = new Mapper( "IDTuple", "l2src", "nodeID");
		packetTracer.subscribe( multihopIDMapper, id++);

		// get source address of all streams if available
		Union<Tuple> packetIdStream = new Union<Tuple>();
		linkBeaconIDMapper.subscribe( packetIdStream, id++);
		linkAdvertisementIDMapper.subscribe( packetIdStream, id++);
		pathAdvertisementIDMapper.subscribe( packetIdStream, id++);
		multihopIDMapper.subscribe(packetIdStream, id++);
		
		// neighbourlist timeout
		// int epoch = 320 * 1000;
		
		// metric: number of packet received last epoch per node
		TupleTimeWindowGroupAggregator packetsLastEpoch =
			new TupleTimeWindowGroupAggregator ( beaconPeriod * packetMultiply , "nodeID", new Counter( "PacketsLastEpoch", "packets"),"packetsLastEpoch");
		packetIdStream.subscribe( packetsLastEpoch, id++);

		// metric: number of valid route announcements last epoch per node
		TupleTimeWindowGroupAggregator pathAnnouncementsLastEpoch2 =
			new TupleTimeWindowGroupAggregator ( pathAdvPeriod * packetMultiply, "nodeID", new Counter( "RoutesLastEpoch", "routeAnnouncements"),"pathAnnouncementsLastEpoch");
		pathAdvertisementMapper.subscribe(pathAnnouncementsLastEpoch2 , id++);

		// metric: number of neighbours reported node last epoch per node
		TupleTimeWindowDistinctGroupAggregator seenByNeighbours =
			new TupleTimeWindowDistinctGroupAggregator ( linkAdvPeriod * packetMultiply, new Counter("NeighbourReportsLastEpochTemp", "sightings"), "seenNode",
					"reportingNode", "seenNode") ;
		linkAdvertisementMapper.subscribe( seenByNeighbours, id++);

		// use "seenNode" as "nodeID"
		Mapper seenByNeighboursIDMapper = new Mapper( "NeighbourReportsLastEpoch", "seenNode", "nodeID", "sightings" , "sightings" );
		seenByNeighbours.subscribe( seenByNeighboursIDMapper, id++);

		// metric: number of neighbours reported node last epoch per node
		TupleTimeWindowDistinctGroupAggregator neighboursSeenLastEpoch =
			new TupleTimeWindowDistinctGroupAggregator ( linkAdvPeriod * packetMultiply, new Counter("NeighbourSeenLastEpochTemp", "sightings"), "reportingNode",
					"reportingNode", "seenNode");
		linkAdvertisementMapper.subscribe( neighboursSeenLastEpoch, id++);
		
		// use "reportingNode" as "nodeID"
		Mapper neighboursSeenLastEpochIDMapper = new Mapper( "NeighbourSeenLastEpoch", "reportingNode", "nodeID", "sightings", "sightings" );
		neighboursSeenLastEpoch.subscribe( neighboursSeenLastEpochIDMapper, id++);
		
		// metric: max path quality reported last epoch
		TupleTimeWindowGroupAggregator maxPathQuality =
			new TupleTimeWindowGroupAggregator ( pathAdvPeriod * packetMultiply, "nodeID", new Max( "MaxPathQuality", "quality"),"maxPathQuality");
		pathAdvertisementMapper.subscribe(maxPathQuality , id++);
		
		// Route analyzer: detects "GoodRoute"s, "RoutingLoop" and performs "LatencyMeasurement"
		AbstractPipe<Tuple,Tuple> routeAnalyzer = new RouteAnalyzer(theSink);
		packetTracer.subscribe( routeAnalyzer, id++);

		// get LatencyMeasurement measurements (?)
		Filter<Tuple> goodRouteFilter = new Filter<Tuple>(
				new AttributePredicate("TupleType",  "LatencyMeasurement" )); 
		routeAnalyzer.subscribe( goodRouteFilter, id++);
		
		// metric: nr of good route reports last epoch // TODO should use dataPeriod ??
		TupleTimeWindowGroupAggregator goodRouteReports =
			new TupleTimeWindowGroupAggregator ( pathAdvPeriod * packetMultiply, "nodeID", new Counter( "GoodRoute", "reports"),"goodRouteReports");
		goodRouteFilter.subscribe(goodRouteReports , id++);

		// get RoutingLoop detections
		Filter<Tuple> routingLoopFilter = new Filter<Tuple>(
				new AttributePredicate("TupleType",  "RoutingLoop" )); 
		routeAnalyzer.subscribe( routingLoopFilter, id++);

		// metric: nr of routing loops last epoch
		TupleTimeWindowGroupAggregator routingLoopReports =
			new TupleTimeWindowGroupAggregator ( pathAdvPeriod * packetMultiply, "nodeID", new Counter( "RoutingLoops", "reports"),"routingLoopReports");
		routingLoopFilter.subscribe(routingLoopReports , id++);

		// get current observation quality -- TODO double the beacon 
		TupleTimeWindowGroupAggregator observationQuality = new TupleTimeWindowGroupAggregator( (320*1000 + linkAdvPeriod * packetMultiply) * 2 , "nodeID", new Ratio ( "ObservationQuality", "seqNr"),"observationQuality");
		seqNrMapper.subscribe( observationQuality, id++ );
		
		// get all metric streams
		Union<Tuple> metricStream = new Union<Tuple>();
		packetsLastEpoch.subscribe( metricStream, id++);
		seenByNeighboursIDMapper.subscribe( metricStream, id++);
		neighboursSeenLastEpochIDMapper.subscribe( metricStream, id++);
		pathAnnouncementsLastEpoch2.subscribe(metricStream,id++);
		goodRouteReports.subscribe( metricStream, id++);
		routingLoopReports.subscribe( metricStream, id++);
		maxPathQuality.subscribe( metricStream, id++);
		observationQuality.subscribe( metricStream, id++);

		// get all event streams
		Union<Tuple> eventStream = new Union<Tuple>();
		seqResetDetector.subscribe( eventStream, id++);
		// TODO latencyObservator.subscribe( eventStream, id++ );
		
		BinaryDecisionTree noPacketReceivedTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"PacketsLastEpoch", "packets", TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree coveredTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"ObservationQuality", "ratio", TreeAttributePredicate.Comparator2.greater, 0.4f));
		BinaryDecisionTree noWitnessTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"NeighbourReportsLastEpoch", "sightings", TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree noNeighboursTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"NeighbourSeenLastEpoch", "sightings", TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree networkPartitionTestB = new BinaryDecisionTree( new TreeAttributePredicate(
				"NodePartitioned", "partitioned", TreeAttributePredicate.Comparator.equal, 1));
		BinaryDecisionTree networkPartitionTestC = new BinaryDecisionTree( new TreeAttributePredicate(
				"NodePartitioned", "partitioned", TreeAttributePredicate.Comparator.equal, 1));
		BinaryDecisionTree noPathTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"RoutesLastEpoch", "routeAnnouncements", TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree noGoodRouteTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"GoodRoute", "reports", TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree routingLoopTest = new BinaryDecisionTree( new TreeAttributePredicate(
				"RoutingLoops", "reports", TreeAttributePredicate.Comparator.greater, 2));
		BinaryDecisionTree nodeCrash = BinaryDecisionTree.createTupleResultNode ("NodeCrash");
		
		coveredTest.setTrue( noPacketReceivedTest );
		coveredTest.setFalse( noPacketReceivedTest );
// HACK TODO to test, if alternative path through decisiont tree causes strange latency behaviour .. coveredTest.setFalse( noWitnessTest );
		noWitnessTest.setTrue ( nodeCrash );
		noWitnessTest.setFalse( noGoodRouteTest );
		noPacketReceivedTest.setTrue ( nodeCrash );
		noPacketReceivedTest.setFalse (  noNeighboursTest );
		noNeighboursTest.setFalse( noPathTest );
		noNeighboursTest.setTrue(  BinaryDecisionTree.createTupleResultNode ("NoNeighbours") );

		noPathTest.setFalse( noGoodRouteTest );
		noPathTest.setTrue( networkPartitionTestB );
		
		// result: NetworkPartitioned (No Parent)
		BinaryDecisionTree networkPartitionNoPath = new BinaryDecisionTree () {
			final TupleAttribute crashedID = new TupleAttribute("crashedNodes");
			final TupleAttribute resultID = new TupleAttribute("result");
			public Tuple invoke( HashMap<Object,Tuple> input) {
				Tuple tuple = Tuple.createTuple("NetworkPartitioned");
				tuple.setStringAttribute(crashedID, input.get("NodePartitioned").getStringAttribute(crashedID));
				tuple.setStringAttribute(resultID, "NoParent");
				return tuple;
			}
		};
		networkPartitionTestB.setTrue( networkPartitionNoPath );
		networkPartitionTestB.setFalse( BinaryDecisionTree.createTupleResultNode ("NoParent"));
		networkPartitionTestB.setDefault(  BinaryDecisionTree.createTupleResultNode ("NoParent") );

		noGoodRouteTest.setFalse(BinaryDecisionTree.createTupleResultNode ("NodeOK") );
		noGoodRouteTest.setTrue( networkPartitionTestC );
		
		// result: NetworkParitioned (No Good Route)
		BinaryDecisionTree networkPartitionNoGoodRoute = new BinaryDecisionTree () {
			final TupleAttribute crashedID = new TupleAttribute("crashedNodes");
			final TupleAttribute resultID = new TupleAttribute("result");
			public Tuple invoke( HashMap<Object,Tuple> input) {
				Tuple tuple = Tuple.createTuple("NetworkPartitioned");
				tuple.setStringAttribute(crashedID, input.get("NodePartitioned").getStringAttribute(crashedID));
				tuple.setStringAttribute(resultID, "NoGoodRoute");
				return tuple;
			}
		};
		networkPartitionTestC.setTrue( networkPartitionNoGoodRoute );
		networkPartitionTestC.setFalse( routingLoopTest);
		networkPartitionTestC.setDefault(  routingLoopTest );
		
		routingLoopTest.setTrue(BinaryDecisionTree.createTupleResultNode ("RoutingFailureLoop") );
		routingLoopTest.setDefault(BinaryDecisionTree.createTupleResultNode ("RoutingFailureGeneral") );
		routingLoopTest.setFalse(BinaryDecisionTree.createTupleResultNode ("RoutingFailureGeneral") );
		
		GroupingEvaluator stateDetector = GroupingEvaluator.createBinaryTreeEvaluator(coveredTest, "nodeID","stateDetector");
		metricStream.subscribe( stateDetector , id++);

//		// check for node 12
//		AbstractSink<Tuple> debugLogger = new AbstractSink<Tuple>() {
//			public void process(Tuple o, int srcID, long timestamp) {
//				if ( o.getIntAttribute("reports") == 0) {
//						logLine( dsnLogWriter, "" + timestamp/1000 + " -- " + o.toString() );
//				}
//				if ( o.getIntAttribute("nodeID") == 6) {
//					logLine( dsnLogWriter, "" + timestamp/1000 + " -- " + o.toString() );
//			}
//			}
//		};
//		goodRouteReports.subscribe( debugLogger, id++);

		// get node state changes
		Filter<Tuple> nodeStateChangeFilter = new Filter<Tuple>( new TupleChangePredicate("nodeID"));
		stateDetector.subscribe( nodeStateChangeFilter, id++);

		// network partition detetction  TODO --
		int packetTracerID = id++;
		int nodeStateChangeFilterID = id++;
		NetworkPartitionDetection partitionDetection = new NetworkPartitionDetection(
				theSink, 2 * pathAdvPeriod * packetMultiply, 10 * 1000, nodeStateChangeFilterID, packetTracerID);
		packetTracer.subscribe( partitionDetection, packetTracerID);
		nodeStateChangeFilter.subscribe( partitionDetection, nodeStateChangeFilterID);
		partitionDetection.subscribe( metricStream, id++);
	
		// log to file
		AbstractSink<Tuple> logger = new AbstractSink<Tuple>() {
			public void process(Tuple o, int srcID, long timestamp) {
				logLine( dsnLogWriter, "" + timestamp/1000 + " -- " + o.toString() );
			}
		};
		nodeStateChangeFilter.subscribe( logger, id++);

		// total bandwith aggregator
		AbstractSink<Tuple> totalDataAggregator = new AbstractSink<Tuple>() {
			int mhType = parser.getValue("MULTIHOP_MULTIHOPPACKET");
			int sympathyType = parser.getValue("MULTIHOP_MH_SYMPATHY");
			final TupleAttribute typeAttribute = new TupleAttribute( "TOS_Msg.type");
			final TupleAttribute mhTypeAttribute = new TupleAttribute( "MultiHopPacket.type");
			final TupleAttribute lengthAttribute = new TupleAttribute( "MultiHopPacket.type");
			public void process(Tuple o, int srcID, long timestamp) {;
				PacketTuple packetTuple = ((PacketTuple) o);
				int type = o.getIntAttribute(typeAttribute);
				if (type != mhType || o.getIntAttribute(mhTypeAttribute) != sympathyType) { 
					totalData += packetTuple.getIntAttribute(lengthAttribute) + 7; // addr(2)+type+grp+crc(2)
				}
			}
		};
		merger.subscribe( totalDataAggregator, id++);
		//////////// END TUPLE APPROACH //////////////////
		
		Scheduler.speed = -1; // batch processing, full speed
		Scheduler.run( merger);
		
		// report observation quality
		logLine( dsnLogWriter, "coverage.mean=" + distinctInWindow.getMean());
		logLine( dsnLogWriter, "coverage.deviation=" + Math.sqrt(distinctInWindow.getVariance()));
		logLine( dsnLogWriter, "data=" + totalData);
		logLine( dsnLogWriter, "observation=" + totalObservationQuality.getObservationQuality());

		// flush and close file
		dsnLogWriter.flush();
		dsnLogWriter.close();

		// distinctInWindow.dump();
		// totalObservationQuality.dump();		
		
		System.out.println("dsnSympathy. Done with "+path);
	}

	/**
	 * @param parser
	 * @param packetType
	 */
	private static void registerPacketType(final PDL parser, String packetType) {
		PacketTemplate template = parser.getPacketTemplate(packetType);
		String fields[] = template.getAttributeNames();
		for (String field : fields ) {
			String fullyQualifiedField = packetType+"."+field;
			Tuple.registerTupleField(fullyQualifiedField );
		}
	}

	/**
	 * 
	 */
	private static void registerTuples() {
		
		Tuple.registerTupleType( "IDTuple", "nodeID");
		Tuple.registerTupleType( "SeqNrTuple", "nodeID", "seqNr");
		Tuple.registerTupleType( "PacketsLastEpoch", "nodeID", "packets");
		Tuple.registerTupleType( "RoutesLastEpoch",  "nodeID", "routeAnnouncements");
		Tuple.registerTupleType( "PathQuality",  "PathAdvertisement.id", "quality");
		Tuple.registerTupleType( "MaxPathQuality",  "nodeID", "quality");
		Tuple.registerTupleType( "PathAnnouncement",  "nodeID", "quality");
		Tuple.registerTupleType( "LinkQuality",  "LinkAdvertisement.id", "id", "quality");
		Tuple.registerTupleType( "NodeSeen",  "reportingNode", "seenNode");
		Tuple.registerTupleType( "PacketTracerTuple",  "l2src", "l2dst", "l3src", "l3dst", "l3seqNr");
		Tuple.registerTupleType( "NeighbourReportsLastEpochTemp",  "seenNode",     "sightings");
		Tuple.registerTupleType( "NeighbourSeenLastEpochTemp",     "reportingNode", "sightings");
		Tuple.registerTupleType( "NeighbourReportsLastEpoch",  "nodeID",  "sightings");
		Tuple.registerTupleType( "NeighbourSeenLastEpoch",     "nodeID", "sightings");
		Tuple.registerTupleType( "NodeCrash",    "nodeID");
		Tuple.registerTupleType( "NoNeighbours", "nodeID");
		Tuple.registerTupleType( "NoParent",      "nodeID");
		Tuple.registerTupleType( "NodeOK",       "nodeID");
		Tuple.registerTupleType( "GoodRoute",    "nodeID", "reports");
		Tuple.registerTupleType( "RoutingLoops", "nodeID", "reports");
		Tuple.registerTupleType( "RoutingFailureLoop",    "nodeID");
		Tuple.registerTupleType( "RoutingFailureGeneral", "nodeID");
		Tuple.registerTupleType( "ObservationQuality", "min", "max", "count", "ratio", "nodeID", "last");
		Tuple.registerTupleType( "NodePartitioned", "partitioned", "nodeID", "crashedNodes");
		Tuple.registerTupleType( "NetworkPartitioned", "nodeID", "crashedNodes", "result");
	}

	/**
	 * @param parser
	 */
	private static void registerPackets(final PDL parser) {
		registerPacketType(parser, "TOS_Msg");
		registerPacketType(parser, "LinkEstimatorBeacon");
		registerPacketType(parser, "LinkAdvertisement");
		registerPacketType(parser, "PathAdvertisement");
	}

	/** pattern to get information [nodeId,time] of injected node crash */
	static Pattern pathPattern = Pattern.compile(".*group\\d++\\.\\d++traffic\\d++\\.(\\d++)die(\\d++).*");

	/** pattern to identify DSN Logs*/
	static Pattern dsnFilter = Pattern.compile("group.*.\\.sim");
}
