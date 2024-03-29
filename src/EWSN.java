

import dsn.DSNConnector;
import edu.uci.ics.jung.visualization.Coordinates;
import gui.SNIFController;
import gui.View;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;

import model.NodeAddress;
import packetparser.PDL;
import packetparser.Parser;
import stream.AbstractPipe;
import stream.AbstractSink;
import stream.AbstractSource;
import stream.Filter;
import stream.Predicate;
import stream.Scheduler;
import stream.TimeTriggered;
import stream.Union;
import stream.tuple.ArrayExtractor;
import stream.tuple.AttributePredicate;
import stream.tuple.BinaryDecisionTree;
import stream.tuple.Counter;
import stream.tuple.DSNPacketSource;
import stream.tuple.DistinctInWindow;
import stream.tuple.GroupingEvaluator;
import stream.tuple.LogReader;
import stream.tuple.Mapper;
import stream.tuple.TopologyAnalyzer;
import stream.tuple.PacketCrcPredicate;
import stream.tuple.PacketTuple;
import stream.tuple.PacketTupleTracer;
import stream.tuple.Ratio;
import stream.tuple.PathAnalyzer;
import stream.tuple.SeqNrResetDetector;
import stream.tuple.TreeAttributePredicate;
import stream.tuple.Tuple;
import stream.tuple.TupleAttribute;
import stream.tuple.TupleChangePredicate;
import stream.tuple.TupleTimeWindowDistinctGroupAggregator;
import stream.tuple.TupleTimeWindowGroupAggregator;

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

public class EWSN extends SNIFController {
	
	
	private static final String PACKETDEFINITION = "packetdefinitions/ewsn07.h";

	public static final boolean usePacketTracer = false;

	public static final boolean useCoveredTest = false;
	
	private static boolean runDebugger = true; // useLog;

	public static final int WORD_MAX_VALUE = 65535;

	private static int totalData;

	// address of sink in observed network
	final static int theSinkID = 0xe0; // 0xea;
	final static NodeAddress theSink = new NodeAddress( theSinkID );

	private static View view;

	private static PDL parser;

	private int nodeList [] = {
	225,  235 , 255 , 295 , 297 ,
	315 , 352 , 364 , 385 , 477 ,
    };

	// W 
	static int W = 10;
	// respective periods
	final static int beaconPeriod = 10 * 1000;
	final static int linkAdvPeriod = 80 * 1000;
	final static int pathAdvPeriod = 80 * 1000;
	final static int dataPeriod = 30 * 1000;

	private void setNodePositions() {
		HashMap<Integer, Coordinates> nodePositions = new HashMap<Integer, Coordinates>();
		// get position. 5 nodes per row. 160 / 4 = 40
		for (int i = 0; i<nodeList.length; i++ ){
				int scale = 5;
				Coordinates coords = new Coordinates( ((i % 5)*30 + 10) *scale,((i/5) * 25 + 12)* scale);
				nodePositions.put( nodeList[i], coords);
		}
		view.setNodeCoordinates(nodePositions);
	}
	
	/**
	 * @param dsnLogWriter
	 * @return
	 */
	private static AbstractSink<PacketTuple> createPacketLogger(final FileWriter dsnLogWriter) {
		AbstractSink<PacketTuple> packetLogger = new AbstractSink<PacketTuple>() {
			public void process(PacketTuple o, int srcID, long timestamp) {
				logLine( dsnLogWriter, "" + timestamp + " " + o.getDsnNode() + " " + o.toString());
			}
		};
		return packetLogger;
	}


	// used to signal run queue
	private static FileWriter dsnLogWriter;	

	public void setup() {
		// create view
		// create graph
		view = new View(this);
		view.establish();
		view.setVisible(true);
		view.setW( W );
		// fix node positions
		setNodePositions();
		
		parser = Parser.readDescription(PACKETDEFINITION);
		// parser.dumpDescription();
	}

	/**
	 * @param args Path to folder containing logFilter logs
	 * @throws Exception
	 */
	// @SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {

		EWSN debugger = new EWSN();
		debugger.setup();

		while (true) {

			// --- let's wait for user first..
			debugger.start = new Object();
			synchronized(debugger.start) {
				debugger.start.wait();
			}
			
			// get W
			W = view.getW();
			// -- create whole graph and connect to GUI
			Filter<PacketTuple> crcFilter = debugger.createAnalysisGraph();

			System.out.println( "ewsn snif demo started..");

			// ---
			AbstractSource<PacketTuple> dsnPacketSource = null;
			DSNConnector dsnConnection = null;

			if (debugger.useLog) {
				LogReader logReader = LogReader.createLogReaderFromFile(debugger.PACKET_INPUT);
				logReader.setParser(parser);
				dsnPacketSource = logReader;
			}

			if (debugger.useDSN) {

				// create log file based on current time
				dsnLogWriter = new FileWriter("log_"+(System.currentTimeMillis()/1000));

				// DNS connection
				dsnConnection = new DSNConnector();
				dsnConnection.registerView(view);
				// is used for Graph
				dsnPacketSource = new DSNPacketSource(dsnConnection, parser );
				AbstractSink<PacketTuple> packetLogger = createPacketLogger(dsnLogWriter);
				dsnPacketSource.subscribe(packetLogger, 0);

				// start DSN sniffer */
				dsnConnection.init();
				dsnConnection.connect();
				dsnConnection.setSnifConfig(parser.getSnifferConfig());
				dsnConnection.start();
				view.setBTConnection( dsnConnection.getSnifGateway() );
			}

			if (runDebugger) {
				dsnLogWriter = null;
				// dsnPacketSource.subscribe( totalDataAggregator, 0);
				dsnPacketSource.subscribe( crcFilter, 0);
			}

			Scheduler.run( dsnPacketSource );

			// flush and close file
			if (dsnLogWriter != null) {
				dsnLogWriter.flush();
				dsnLogWriter.close();
			}

			// stop DSN
			if (debugger.useDSN) {
				dsnConnection.stopConnection();
			}

			// update GUI
			view.simulationStopped();
		}
	}

	/**
	 * @return
	 */
	private Filter<PacketTuple> createAnalysisGraph() {

		// create crc filter
		Predicate<PacketTuple> crcCheck = new PacketCrcPredicate(parser);
		Filter<PacketTuple> crcFilter = new Filter<PacketTuple>(crcCheck);

		// total bandwith aggregator
		// AbstractSink<Tuple> totalDataAggregator = createTotalBandwidthAggregator();

		// get overall observation quality
		// TupleGroupAggregator totalObservationQuality = new TupleGroupAggregator( new Ratio ( "ObservationQuality", "seqNr"), "nodeID", "totalObservationQuality");
		// seqNrMapper.subscribe( totalObservationQuality, 0 );

		// metric: max path quality reported last 2 epochs
		// TupleTimeWindowGroupAggregator maxPathQuality =
		// 	new TupleTimeWindowGroupAggregator ( 2 * epoch, "nodeID", new Max( "MaxPathQuality", "quality"),"maxPathQuality");
		// pathAdvertisementMapper.subscribe(maxPathQuality , 0);

		// filter packets with identical content reported by different DSN nodes within short time (20 ms)
		DistinctInWindow distinctInWindow = new DistinctInWindow(1000);
		Filter<PacketTuple> dupFilter = new Filter<PacketTuple>(
				distinctInWindow);
		crcFilter.subscribe(dupFilter, 0);

		// extrac layer2 source
		Mapper packetIdStream = new Mapper("IDTuple", "bmac_msg_st.source", "nodeID");
		dupFilter.subscribe(packetIdStream, 0);
		Mapper groupIdStream = new Mapper("groupID", "bmac_msg_st.source", "groupID");
		dupFilter.subscribe(groupIdStream, 0);

		// get linkBeacon tuple stream
		Filter<Tuple> linkBeaconFilter = new Filter<Tuple>(
				new AttributePredicate("ccc_packet_st.type", parser
						.getValue("BEACON_TYPE")));
		dupFilter.subscribe(linkBeaconFilter, 0);

		// extract layer 2 source address and seqNr from beacons
		Mapper seqNrMapper = new Mapper("SeqNrTuple", "beacon_packet.node_id",
				"nodeID", "beacon_packet.seq_nr", "seqNr");
		linkBeaconFilter.subscribe(seqNrMapper, 0);

		// check for seq nr reset on beacon seq nr
		SeqNrResetDetector seqResetDetector = new SeqNrResetDetector("nodeID",
				"seqNr", WORD_MAX_VALUE, 10);
		seqNrMapper.subscribe(seqResetDetector, 0);

		// get linkAdvertisement tuple stream
		Filter<Tuple> linkAdvertisementFilter = new Filter<Tuple>(
				new AttributePredicate("ccc_packet_st.type", parser
						.getValue("ADVERT_TYPE")));
		dupFilter.subscribe(linkAdvertisementFilter, 0);

		// translate LinkAdvertisementBeacons into Neighbour sightings
		ArrayExtractor linkAdvertisementExtractor = new ArrayExtractor(
				"LinkQuality", "advert_packet.neighbours.length",
				"advert_packet.neighbours", "advert_packet.node_id", "node_id",
				"quality");
		linkAdvertisementFilter.subscribe(linkAdvertisementExtractor, 0);

		// ignore empty entries
		Filter<Tuple> neighbourTableFilter = new Filter<Tuple>(
				new Predicate<Tuple>() {
					final TupleAttribute nodeIDAttribute = new TupleAttribute(
							"node_id");

					public boolean invoke(Tuple o, long timestamp) {
						return o.getIntAttribute(nodeIDAttribute) != 0;
					}
				});
		linkAdvertisementExtractor.subscribe(neighbourTableFilter, 0);

		// map fields for neighbour count operators
		Mapper linkAdvertisementMapper = new Mapper("NodeSeen",
				"advert_packet.node_id", "reportingNode", "node_id", "seenNode");
		neighbourTableFilter.subscribe(linkAdvertisementMapper, 0);

		// get pathAdvertisementFilter tuple stream
		Filter<Tuple> pathAdvertisementFilter = new Filter<Tuple>(
				new AttributePredicate("ccc_packet_st.type", parser
						.getValue("DISTANCE_TYPE")));
		dupFilter.subscribe(pathAdvertisementFilter, 0);

		// extract path quality
		Mapper pathAdvertisementMapper = new Mapper("PathAnnouncement",
				"distance_packet.node_id", "nodeID",
				"distance_packet.distance", "quality",
				"distance_packet.round_nr", "round");
		pathAdvertisementFilter.subscribe(pathAdvertisementMapper, 0);

		// get multiHopPacket stream
		Filter<Tuple> multiHopFilter = new Filter<Tuple>(
				new AttributePredicate("ccc_packet_st.type", parser
						.getValue("DATA_TYPE")));
		dupFilter.subscribe(multiHopFilter, 0);

		AbstractPipe<Tuple, Tuple> packetTracer;
		if (usePacketTracer) {
			// get Tracer data directly from packet
			// PacketTracer reports last node sending a packet before // HACK - l3src used as l3dst!
			packetTracer = new PacketTupleTracer("PacketTracerTuple",
					"bmac_msg_st.destination", "data_packet.node_id",
					"data_packet.node_id", "data_packet.seq_nr");
			multiHopFilter.subscribe(packetTracer, 0);
		} else {
			packetTracer = new Mapper("PacketTracerTuple",
					"bmac_msg_st.source", "l2src", "bmac_msg_st.destination",
					"l2dst", "data_packet.node_id", "l3src");
			multiHopFilter.subscribe(packetTracer, 0);
		}

		// metric: number of packet received (W times beacon period) per node
		TupleTimeWindowGroupAggregator packetCount = new TupleTimeWindowGroupAggregator(
				W * beaconPeriod, "nodeID", new Counter("PacketsLastEpoch",
						"packets"), "packetsLastEpoch");
		packetIdStream.subscribe(packetCount, 0);
		groupIdStream.subscribe(packetCount, 0);

		// metric: number of valid route announcements ..e
		TupleTimeWindowGroupAggregator pathAnnouncementsLastEpoch2 = new TupleTimeWindowGroupAggregator(
				W * pathAdvPeriod, "nodeID", new Counter("RoutesLastEpoch",
						"routeAnnouncements"), "pathAnnouncementsLastEpoch");
		pathAdvertisementMapper.subscribe(pathAnnouncementsLastEpoch2, 0);
		groupIdStream.subscribe(pathAnnouncementsLastEpoch2, 0);
		
		// metric: number of neighbours reported node ..
		TupleTimeWindowDistinctGroupAggregator seenByNeighbours = new TupleTimeWindowDistinctGroupAggregator(
				W * linkAdvPeriod, new Counter("NeighbourReportsLastEpochTemp",
						"sightings"), "seenNode", "reportingNode", "seenNode");
		linkAdvertisementMapper.subscribe(seenByNeighbours, 0);
		groupIdStream.subscribe(seenByNeighbours, 0);

		// use "seenNode" as "nodeID"
		Mapper seenByNeighboursIDMapper = new Mapper(
				"NeighbourReportsLastEpoch", "seenNode", "nodeID", "sightings",
				"sightings");
		seenByNeighbours.subscribe(seenByNeighboursIDMapper, 0);

		// metric: number of neighbours seen by node node ..
		TupleTimeWindowDistinctGroupAggregator neighboursSeen = new TupleTimeWindowDistinctGroupAggregator(
				W * linkAdvPeriod, new Counter("NeighbourSeenLastEpochTemp",
						"sightings"), "reportingNode", "reportingNode",
				"seenNode");
		linkAdvertisementMapper.subscribe(neighboursSeen, 0);
		groupIdStream.subscribe(neighboursSeen, 0);

		// use "reportingNode" as "nodeID"
		Mapper neighboursSeenLastEpochIDMapper = new Mapper(
				"NeighbourSeenLastEpoch", "reportingNode", "nodeID",
				"sightings", "sightings");
		neighboursSeen.subscribe(neighboursSeenLastEpochIDMapper, 0);

		// Route analyzer: detects "GoodRoute"s, "RoutingLoop" and performs "LatencyMeasurement"
		AbstractPipe<Tuple, Tuple> routeAnalyzer = new PathAnalyzer(theSink);
		packetTracer.subscribe(routeAnalyzer, 0);

		// get LatencyMeasurement measurements (?)
		Filter<Tuple> goodRouteFilter = new Filter<Tuple>(
				new AttributePredicate("TupleType", "LatencyMeasurement"));
		routeAnalyzer.subscribe(goodRouteFilter, 0);

		// metric: nr of good route reports ..
		TupleTimeWindowGroupAggregator goodRouteReports = new TupleTimeWindowGroupAggregator(
				W * dataPeriod, "nodeID", new Counter("GoodRoute", "reports"),
				"goodRouteReports");
		goodRouteFilter.subscribe(goodRouteReports, 0);
		groupIdStream.subscribe(goodRouteReports, 0);

		// get RoutingLoop detections
		Filter<Tuple> routingLoopFilter = new Filter<Tuple>(
				new AttributePredicate("TupleType", "RoutingLoop"));
		routeAnalyzer.subscribe(routingLoopFilter, 0);

		// metric: nr of routing loops ..
		TupleTimeWindowGroupAggregator routingLoopReports = new TupleTimeWindowGroupAggregator(
				W * dataPeriod, "nodeID",
				new Counter("RoutingLoops", "reports"), "routingLoopReports");
		routingLoopFilter.subscribe(routingLoopReports, 0);
		groupIdStream.subscribe(routingLoopReports, 0);

		// get observation quality .. -- requires smoothing
		TupleTimeWindowDistinctGroupAggregator observationQuality = new TupleTimeWindowDistinctGroupAggregator(
				4 * W * beaconPeriod, new Ratio("ObservationQuality", "seqNr"),
				"nodeID", "nodeID", "seqNr");
		seqNrMapper.subscribe(observationQuality, 0);
		groupIdStream.subscribe(observationQuality, 0);

		// reboots last epoch
		TupleTimeWindowGroupAggregator rebootCount = new TupleTimeWindowGroupAggregator(
				W * beaconPeriod, "nodeID", new Counter("RebootsLastEpoch",
						"reboots"), "rebootsLastEpoch");
		seqResetDetector.subscribe(rebootCount, 0);
		groupIdStream.subscribe(rebootCount, 0);

		// get all metric streams
		Union<Tuple> metricStream = new Union<Tuple>();
		packetCount.subscribe(metricStream, 0);
		seenByNeighboursIDMapper.subscribe(metricStream, 0);
		neighboursSeenLastEpochIDMapper.subscribe(metricStream, 0);
		pathAnnouncementsLastEpoch2.subscribe(metricStream, 0);
		goodRouteReports.subscribe(metricStream, 0);
		routingLoopReports.subscribe(metricStream, 0);
		// maxPathQuality.subscribe( metricStream, 0);
		observationQuality.subscribe(metricStream, 0);
		rebootCount.subscribe(metricStream, 0);

		// get all event streams
		Union<Tuple> eventStream = new Union<Tuple>();
		seqResetDetector.subscribe(eventStream, 0);
		// TODO latencyObservator.subscribe( eventStream, 0 );

		BinaryDecisionTree firstTest = createDecisionTree();

		GroupingEvaluator stateDetector = GroupingEvaluator
				.createBinaryTreeEvaluator(firstTest, "nodeID",
						"stateDetector");
		metricStream.subscribe(stateDetector, 0);

		// get node state changes
		Filter<Tuple> nodeStateChangeFilter = new Filter<Tuple>(
				new TupleChangePredicate("nodeID"));
		stateDetector.subscribe(nodeStateChangeFilter, 0);

		// network partition detetction
		int packetTracerID = 1;
		int nodeStateChangeFilterID = 2;
		TopologyAnalyzer partitionDetection = new TopologyAnalyzer(
				theSink, W * pathAdvPeriod, 10 * 1000, nodeStateChangeFilterID,
				packetTracerID);
		packetTracer.subscribe(partitionDetection, packetTracerID);
		nodeStateChangeFilter.subscribe(partitionDetection,
				nodeStateChangeFilterID);
		partitionDetection.subscribe(metricStream, 0);

		// log to file
		AbstractSink<Tuple> logger = new AbstractSink<Tuple>() {
			public void process(Tuple o, int srcID, long timestamp) {
				logLine(dsnLogWriter, "" + timestamp / 1000 + " -- "
						+ o.toString());
			}
		};
		nodeStateChangeFilter.subscribe(logger, 0);
		eventStream.subscribe(logger, 0);

		// metricStream.subscribe(logger, 0);
		// routeAnalyzer.subscribe(logger, 0);
		// packetTupleMapper.subscribe(logger, 0);		

		// map "from", "to" -> "linkID=from#to
		Tuple.registerTupleType("LinkTuple", "linkID");
		AbstractPipe<Tuple, Tuple> linkEnumeratorNeighbours = new AbstractPipe<Tuple, Tuple>() {
			final TupleAttribute idField = new TupleAttribute("linkID");

			final TupleAttribute fromAttribute = new TupleAttribute(
					"reportingNode");

			final TupleAttribute toAttribute = new TupleAttribute("seenNode");

			public void process(Tuple o, int srcID, long timestamp) {
				int from = o.getIntAttribute(fromAttribute);
				int to = o.getIntAttribute(toAttribute);
				Tuple tuple = Tuple.createTuple("LinkTuple");
				tuple.setAttribute(idField, "" + from + "#" + to);
				transfer(tuple, timestamp);
			}
		};
		linkAdvertisementMapper.subscribe(linkEnumeratorNeighbours, 0);
		// metric: nr of times a neighbour was was reported last ..
		TupleTimeWindowGroupAggregator linkNeighboursLastEpoch = new TupleTimeWindowGroupAggregator(
				W * linkAdvPeriod, "linkID", new Counter("LinkListed",
						"reports"), "linkNeighboursLastEpoch");
		linkEnumeratorNeighbours.subscribe(linkNeighboursLastEpoch, 0);

		AbstractPipe<Tuple, Tuple> linkEnumeratorData = new AbstractPipe<Tuple, Tuple>() {
			final TupleAttribute idField = new TupleAttribute("linkID");

			final TupleAttribute l2srcAttribute = new TupleAttribute("l2src");

			final TupleAttribute l2dstAttribute = new TupleAttribute("l2dst");

			public void process(Tuple o, int srcID, long timestamp) {
				int from = o.getIntAttribute(l2srcAttribute);
				int to = o.getIntAttribute(l2dstAttribute);
				Tuple tuple = Tuple.createTuple("LinkTuple");
				tuple.setAttribute(idField, "" + from + "#" + to);
				transfer(tuple, timestamp);
			}
		};
		packetTracer.subscribe(linkEnumeratorData, 0);
		// metric: nr of times a packet was sent across a link ..
		TupleTimeWindowGroupAggregator linkDataLastEpoch = new TupleTimeWindowGroupAggregator(
				W * dataPeriod, "linkID", new Counter("LinkData", "reports"),
				"linkDataLastEpoch");
		linkEnumeratorData.subscribe(linkDataLastEpoch, 0);

		// connect to GUI
		createGuiSink(dupFilter, linkAdvertisementMapper, metricStream,
				eventStream, nodeStateChangeFilter, linkNeighboursLastEpoch,
				linkDataLastEpoch, seqNrMapper, multiHopFilter,
				pathAdvertisementMapper, linkBeaconFilter);

		return crcFilter;
	}

	/**
	 * @return
	 */
	private BinaryDecisionTree createDecisionTree() {
		BinaryDecisionTree noPacketReceivedTest = new BinaryDecisionTree(
				new TreeAttributePredicate("PacketsLastEpoch", "packets",
						TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree noNeighboursTest = new BinaryDecisionTree(
				new TreeAttributePredicate("NeighbourSeenLastEpoch",
						"sightings", TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree networkPartitionTestB = new BinaryDecisionTree(
				new TreeAttributePredicate("NodePartitioned", "partitioned",
						TreeAttributePredicate.Comparator.equal, 1));
		BinaryDecisionTree networkPartitionTestC = new BinaryDecisionTree(
				new TreeAttributePredicate("NodePartitioned", "partitioned",
						TreeAttributePredicate.Comparator.equal, 1));
		BinaryDecisionTree noPathTest = new BinaryDecisionTree(
				new TreeAttributePredicate("RoutesLastEpoch",
						"routeAnnouncements",
						TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree noGoodRouteTest = new BinaryDecisionTree(
				new TreeAttributePredicate("GoodRoute", "reports",
						TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree routingLoopTest = new BinaryDecisionTree(
				new TreeAttributePredicate("RoutingLoops", "reports",
						TreeAttributePredicate.Comparator.greater, 0));
		BinaryDecisionTree noRebootsTest = new BinaryDecisionTree(
				new TreeAttributePredicate("RebootsLastEpoch", "reboots",
						TreeAttributePredicate.Comparator.equal, 0));
		BinaryDecisionTree nodeCrash = BinaryDecisionTree
				.createTupleResultNode("NodeCrash");
		BinaryDecisionTree nodeOK = BinaryDecisionTree
				.createTupleResultNode("NodeOK");
		BinaryDecisionTree sinkTest = new BinaryDecisionTree(
				new TreeAttributePredicate("PacketsLastEpoch", "nodeID",
						TreeAttributePredicate.Comparator.equal, theSinkID));
		BinaryDecisionTree waitingPackets = BinaryDecisionTree
				.createTupleResultNode("WaitingPackets");
		BinaryDecisionTree waitingNeighbours = BinaryDecisionTree
				.createTupleResultNode("WaitingNeighbours");
		BinaryDecisionTree waitingPath = BinaryDecisionTree
				.createTupleResultNode("WaitingPath");
		BinaryDecisionTree waitingRoute = BinaryDecisionTree
				.createTupleResultNode("WaitingRoute");
		BinaryDecisionTree notCovert = BinaryDecisionTree
				.createTupleResultNode("NotCovert");

		// result: NetworkPartitioned 
		BinaryDecisionTree networkPartitionNoPath = new BinaryDecisionTree() {
			final TupleAttribute crashedID = new TupleAttribute("crashedNodes");

			final TupleAttribute resultID = new TupleAttribute("result");

			final String[] attributes = new String[] { "crashedNodes", "result" };

			public Tuple invoke(HashMap<Object, Tuple> input) {
				Tuple tuple = Tuple.createTuple("NetworkPartitioned");
				tuple.setStringAttribute(crashedID, input
						.get("NodePartitioned").getStringAttribute(crashedID));
				tuple.setStringAttribute(resultID, "NoParent");
				return tuple;
			}

			public String getResultType() {
				return "NetworkPartitioned";
			}

			public String[] getResultAttributes() {
				return attributes;
			}
		};

		// result: NetworkParitioned
		BinaryDecisionTree networkPartitionNoGoodRoute = new BinaryDecisionTree() {
			final TupleAttribute crashedID = new TupleAttribute("crashedNodes");

			final TupleAttribute resultID = new TupleAttribute("result");

			final String[] attributes = new String[] { "crashedNodes", "result" };

			public Tuple invoke(HashMap<Object, Tuple> input) {
				Tuple tuple = Tuple.createTuple("NetworkPartitioned");
				tuple.setStringAttribute(crashedID, input
						.get("NodePartitioned").getStringAttribute(crashedID));
				tuple.setStringAttribute(resultID, "NoGoodRoute");
				return tuple;
			}

			public String getResultType() {
				return "NetworkPartitioned";
			}

			public String[] getResultAttributes() {
				return attributes;
			}
		};
		BinaryDecisionTree firstTest = null;
		if (useCoveredTest) {

			BinaryDecisionTree coveredTest = new BinaryDecisionTree(
					new TreeAttributePredicate("ObservationQuality", "ratio",
							TreeAttributePredicate.Comparator2.greater, 0.7f));

			BinaryDecisionTree noPacketReceivedTest2 = new BinaryDecisionTree(
					new TreeAttributePredicate("PacketsLastEpoch", "packets",
							TreeAttributePredicate.Comparator.equal, 0));
			
			coveredTest.setTrue(noPacketReceivedTest);
			coveredTest.setFalse(noPacketReceivedTest2);

			// Not Covert - if good route OK, otherwise complain
			noPacketReceivedTest2.setTrue(nodeCrash);
			noPacketReceivedTest2.setFalse(notCovert);
			noPacketReceivedTest2.setDefault(notCovert);
			
			firstTest = coveredTest;
		} else {
			firstTest = noPacketReceivedTest;
		}

		noPacketReceivedTest.setTrue(nodeCrash);
		noPacketReceivedTest.setFalse(noRebootsTest);
		noPacketReceivedTest.setDefault(waitingPackets);

		noRebootsTest.setFalse(BinaryDecisionTree
				.createTupleResultNode("NodeReboot"));
		noRebootsTest.setTrue(sinkTest);
		noRebootsTest.setDefault(sinkTest);

		sinkTest.setTrue(nodeOK);
		sinkTest.setFalse(noGoodRouteTest);

		noGoodRouteTest.setFalse(BinaryDecisionTree
				.createTupleResultNode("NodeOK"));
		noGoodRouteTest.setTrue(noNeighboursTest);
		noGoodRouteTest.setDefault(waitingRoute);

		noNeighboursTest.setFalse(noPathTest);
		noNeighboursTest.setTrue(BinaryDecisionTree
				.createTupleResultNode("NoNeighbours"));
		noNeighboursTest.setDefault(waitingNeighbours);

		// no path (incl. network part test)
		noPathTest.setFalse(networkPartitionTestC); // next test
		noPathTest.setTrue(networkPartitionTestB);
		noPathTest.setDefault(waitingPath);

		networkPartitionTestB.setTrue(networkPartitionNoPath);
		networkPartitionTestB.setFalse(BinaryDecisionTree
				.createTupleResultNode("NoParent"));
		networkPartitionTestB.setDefault(BinaryDecisionTree
				.createTupleResultNode("NoParent"));

		// network partition test
		networkPartitionTestC.setTrue(networkPartitionNoGoodRoute);
		networkPartitionTestC.setFalse(routingLoopTest);
		networkPartitionTestC.setDefault(routingLoopTest);

		// routing loop
		routingLoopTest.setTrue(BinaryDecisionTree
				.createTupleResultNode("RoutingFailureLoop"));
		routingLoopTest.setDefault(BinaryDecisionTree
				.createTupleResultNode("RoutingFailureGeneral"));
		routingLoopTest.setFalse(BinaryDecisionTree
				.createTupleResultNode("RoutingFailureGeneral"));


		// end of tree
		return firstTest;
	}

	/**
	 * @param dupFilter
	 * @param linkAdvertisementMapper
	 * @param metricStream
	 * @param eventStream
	 * @param nodeStateChangeFilter
	 * @param linkNeighboursCount
	 * @param linkDataCount
	 * @param seqNrMapper 
	 * @param multiHopFilter 
	 * @param pathAdvertisementMapper 
	 * @param linkBeaconFilter 
	 */
	private static void createGuiSink(Filter<PacketTuple> dupFilter, Mapper linkAdvertisementMapper,
			Union<Tuple> metricStream, Union<Tuple> eventStream, Filter<Tuple> nodeStateChangeFilter,
			TupleTimeWindowGroupAggregator linkNeighboursCount, TupleTimeWindowGroupAggregator linkDataCount,
			AbstractPipe<Tuple, Tuple> seqNrMapper, AbstractPipe<Tuple, Tuple> multiHopFilter,
			AbstractPipe<Tuple, Tuple> pathAdvertisementMapper, AbstractPipe<Tuple, Tuple> linkBeaconFilter) {
		// GUI
		AbstractSink<Tuple> guiSink = new AbstractSink<Tuple>() {
			HashMap<Integer,Metrics> nodeInfo = new HashMap<Integer,Metrics>();
			class Metrics {
				int packetsSend = 0;
				int nrNeighbours = 0;
				int nrPathAnnouncement = 0;
				int nrReboots = 0;
				int lastPathQuality = -1;
				int nrRoutingLoops = 0;
				double observationQuatlity = 0;
				int lastSeqNr = -1;
				long lastBeacon  = -1;
				long lastLinkAdv  = -1;
				long lastPathAdv  = -1;
				long lastPathRound = -1;
				long lastData     = -1;
				int addr;
				protected String nodeState;
				protected int battery;
				

				/**
				 * @param addr
				 */
				public Metrics(int addr) {
					this.addr = addr;
				}
				/**
				 * @param metric
				 * @return
				 */
				public String toString() {
					Metrics metric = this;
					String metricInfo = "" + addr + "\n" +
					NumberFormat.getPercentInstance().format( metric.observationQuatlity) + "\n"+
					metric.packetsSend + " ["+ beaconPeriod*W/1000+"]\n" +

					metric.lastSeqNr + "\n" +
					metric.lastBeacon + " s\n" +

					metric.nrNeighbours + " ["+ linkAdvPeriod*W/1000+"s]\n" +
					metric.lastLinkAdv + " s\n" +

					metric.nrPathAnnouncement + " ["+ pathAdvPeriod*W/1000+"s]\n" +
					metric.lastPathQuality + "\n" +
					metric.lastPathRound + "\n" +
					metric.lastPathAdv + " s\n" +
					metric.lastData + " s\n" +
					
					metric.nrReboots + "\n" +
					metric.nrRoutingLoops + " ["+ dataPeriod*W/1000+"s]\n" +

					metric.battery/1000 + "." +((metric.battery / 10) % 100) + " V\n" +
					metric.nodeState;
					return metricInfo;
				}
			}
			
			Metrics getNodeInfo( int addr){
				Metrics metric = nodeInfo.get( addr );
				if (metric == null){
					metric = new Metrics(addr);
					nodeInfo.put( addr, metric);
				}
				return metric;
			}
			public void process(Tuple o, int srcID, long timestamp) {
				int addr;
				String type;
				Metrics metric;

				switch (srcID) {
					case 1:				
						view.nodeSeen(o.getIntAttribute(bmac_src_Attribute)+1);
						view.nodeSeen(o.getIntAttribute(bmac_dst_Attribute)+1);
						break;
					case 2:
						view.nodeSeen(o.getIntAttribute(seenNode_Attribute)+1);
						break;
					case 3:
						addr = o.getIntAttribute(nodeID_Attribute) +1;
						type = o.getType();
						metric = getNodeInfo( addr );
						metric.nodeState = type;
						if (type.equals("NodeOK")) {
							view.setNodeState(addr, Color.green);
						} else if (type.equals("NodeReboot")) {
							view.setNodeState(addr, Color.yellow);
						} else if (type.startsWith("Waiting")){
							view.setNodeState(addr, Color.gray);
						} else if (type.startsWith("NotCovert")){
							view.setNodeState(addr, Color.orange);
						} else {
							view.setNodeState(addr, Color.red);
						}
						break;
					case 4:
						addr = o.getIntAttribute(nodeID_Attribute) +1;
						metric = getNodeInfo( addr );
						metric.nrReboots++;
						break;
					case 5:
					case 6:
						String link = (String) o.getAttribute(linkID_Attribute);
						String parts[] = link.split("#");
						int from = Integer.parseInt( parts[0]) + 1;
						int to = Integer.parseInt( parts[1])   + 1;
						int reports = o.getIntAttribute(reports_Attribute);
						metric = getNodeInfo(from);
						metric.lastLinkAdv = timestamp / 1000;
						if (srcID == 5) {
							view.setLinkNeigbours( from, to, reports);
						} else {
							view.setLinkData( from, to, reports);
						}
						break;
					case 7:
						// metric stream
						addr = o.getIntAttribute(nodeID_Attribute) + 1;
						type = o.getType();
						metric = getNodeInfo( addr );
						if (type.equals("PacketsLastEpoch")){
							metric.packetsSend = o.getIntAttribute(packets_Attribute);
						} else if (type.equals("RoutesLastEpoch")){
							metric.nrPathAnnouncement = o.getIntAttribute(routeAnnouncements_Attribute);
						} else if (type.equals("MaxPathQuality")){
							metric.lastPathQuality = o.getIntAttribute(quality_Attribute);
						} else if (type.equals("RoutingLoops")){
							metric.nrRoutingLoops = o.getIntAttribute(reports_Attribute);
						} else if (type.equals("NeighbourSeenLastEpoch")){
							metric.nrNeighbours = o.getIntAttribute(sightings_Attribute);
						} else if (type.equals("ObservationQuality")){
							Object ratio = o.getAttribute(ratio_Attribute);
							if (ratio instanceof Double){
								metric.observationQuatlity = (Double) ratio;
							} else {
								System.out.println("Strange ratio: " + o);
							}
						}
						
						String metricInfo = metric.toString();
						view.setNodeMetrics( addr, metricInfo);
						break;
					case 8:
						// seq nr
						addr = o.getIntAttribute(nodeID_Attribute) + 1;
						metric = getNodeInfo( addr );
						metric.lastSeqNr = o.getIntAttribute(seqNr_Attribute);
						metric.lastBeacon = timestamp / 1000;
						break;
					case 9:
						// path adv
						addr = o.getIntAttribute(nodeID_Attribute) + 1;
						metric = getNodeInfo( addr );
						metric.lastPathAdv = timestamp / 1000;
						metric.lastPathRound = o.getIntAttribute(round_Attribute);
						metric.lastPathQuality = o.getIntAttribute(quality_Attribute);
						break;
					case 10:
						addr = o.getIntAttribute(node_id_Attribute) + 1;
						metric = getNodeInfo( addr );
						metric.lastData = timestamp / 1000;
						break;
					case 11:
						addr = o.getIntAttribute(node_id_Attribute) + 1;
						metric = getNodeInfo( addr );
						metric.battery = o.getIntAttribute(beacon_packet_battery_Attribute);
						break;
				}
			}
		};
		dupFilter.subscribe(guiSink, 1);
		linkAdvertisementMapper.subscribe(guiSink, 2);		
		nodeStateChangeFilter.subscribe(guiSink,3);
		eventStream.subscribe(guiSink, 4);
		linkNeighboursCount.subscribe(guiSink, 5);
		linkDataCount.subscribe(guiSink, 6);
		metricStream.subscribe(guiSink, 7);
		seqNrMapper.subscribe(guiSink, 8);
		pathAdvertisementMapper.subscribe(guiSink, 9);
		multiHopFilter.subscribe(guiSink, 10);
		linkBeaconFilter.subscribe(guiSink, 11);
		
		Scheduler.registerClockView( new TimeTriggered() {
			public void handleTimerEvent(long timestamp) {
				view.setTime( ""+(timestamp / 1000)+ " s");
			}
		});
	}

	/**
	 * @return
	 */
	public static AbstractSink<Tuple> createTotalBandwidthAggregator() {
		AbstractSink<Tuple> totalDataAggregator = new AbstractSink<Tuple>() {
			public void process(Tuple o, int srcID, long timestamp) {;
				PacketTuple packetTuple = ((PacketTuple) o);
				totalData += packetTuple.getIntAttribute("bmac_msg_st.length") + 9; // src(2)+dst(2)+flags+type+length=crc(2)
			}
		};
		return totalDataAggregator;
	}



	private static void logLine( FileWriter writer, String text)  {
		System.out.println( text );
		if (writer != null) {
			try {
				writer.write(text+"\n");
				writer.flush();
			} catch (IOException e) { /** */}
		}
	}
	
	static final TupleAttribute bmac_src_Attribute = new TupleAttribute("bmac_msg_st.source");
	static final TupleAttribute bmac_dst_Attribute = new TupleAttribute("bmac_msg_st.destination");
	static final TupleAttribute seenNode_Attribute = new TupleAttribute("seenNode");
	static final TupleAttribute nodeID_Attribute = new TupleAttribute("nodeID");
	static final TupleAttribute node_id_Attribute = new TupleAttribute("node_id");
	static final TupleAttribute linkID_Attribute = new TupleAttribute("linkID");
	static final TupleAttribute reports_Attribute = new TupleAttribute("reports");
	static final TupleAttribute routeAnnouncements_Attribute = new TupleAttribute("routeAnnouncements");
	static final TupleAttribute round_Attribute = new TupleAttribute("round");
	static final TupleAttribute quality_Attribute = new TupleAttribute("quality");
	static final TupleAttribute ratio_Attribute = new TupleAttribute("ratio");
	static final TupleAttribute packets_Attribute = new TupleAttribute("packets");
	static final TupleAttribute sightings_Attribute = new TupleAttribute("sightings");
	static final TupleAttribute seqNr_Attribute = new TupleAttribute("seqNr");
	static final TupleAttribute beacon_packet_battery_Attribute = new TupleAttribute("beacon_packet.battery");
}
