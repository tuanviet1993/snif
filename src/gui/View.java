package gui;


import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.GridLayout;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import stream.Scheduler;
import edu.uci.ics.jung.graph.ArchetypeEdge;
import edu.uci.ics.jung.graph.ArchetypeVertex;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.decorators.EdgePaintFunction;
import edu.uci.ics.jung.graph.decorators.EdgeShape;
import edu.uci.ics.jung.graph.decorators.EdgeStringer;
import edu.uci.ics.jung.graph.decorators.EdgeStrokeFunction;
import edu.uci.ics.jung.graph.decorators.VertexPaintFunction;
import edu.uci.ics.jung.graph.decorators.VertexStringer;
import edu.uci.ics.jung.graph.impl.DirectedSparseEdge;
import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;
import edu.uci.ics.jung.graph.impl.DirectedSparseVertex;
import edu.uci.ics.jung.utils.UserData;
import edu.uci.ics.jung.visualization.AbstractLayout;
import edu.uci.ics.jung.visualization.Coordinates;
import edu.uci.ics.jung.visualization.FRLayout;
import edu.uci.ics.jung.visualization.GraphZoomScrollPane;
import edu.uci.ics.jung.visualization.PickedState;
import edu.uci.ics.jung.visualization.PluggableRenderer;
import edu.uci.ics.jung.visualization.ShapePickSupport;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;

public class View extends JFrame implements ActionListener, ChangeListener {

	private boolean haveCoordinates = false;
	private HashMap<Integer, Coordinates> nodeCoordinates = null;

	/** whole graph */
	private Graph g;
	
	private SNIFController controller;
	
	private VisualizationViewer vv;
	private AbstractLayout layout;
	private String oldDescription, oldMetrics, oldTime;
	
	private JSlider speedSlider;
	private JTextArea textArea, descriptionArea, metricsArea, timeArea;
	private String description, metrics;
	private boolean showVertexLabels = true;
	private boolean showEdgeLabels = true;
	
	private final String vertexKey = "vertex";
	private final String edgeKey = "edge";

	private final String metricsKey = "metrics";
	private final String statusKey = "status";
    private final String packetCountKey = "packetCount";
    private final String neighboutSeenKey = "neighbourSeen";
    private final String instructions =
        "<html>Use the mouse to select multiple vertices"+
        "<p>either by dragging a region, or by shift-clicking"+
        "<p>on multiple vertices."+
        "<p>With ctrl-A you can select all vertices."+
        "<p>As you select vertices, they become part of a"+
        "<p>cluster, whose metrics are showed at the right side"+
        "<p>and which you can drag with the mouse."+
        "<p>By pressing \"Connect\" you initialize a BTNode"+
        "<p>discovery. As soon as BTNode is discovered, the"+
        "<p>application tries to set up a connection."+
        "<p>\"Save\" allows you to store the whole net traffic"+
        "<p>into a file, which can be replayed later with \"Open\""+
        "<p>During this simulation you can adapt the speed with"+
        "<p>the \"Time Factor\" slider."+
        "<p>For visualization purposes use \"Reorder\", which tries"+
        "<p>to optimize the ordering of the graph, zooming and"+
        "<p>labeling."+
        "</html>";

    final static String d = "NodeID\n" +
	"Observation\n" +
	"#Packets tx\n" +
	"Last seq nr\n" +
	"Last beacon\n" +
	"#Neighbours\n" +
	"Last link adv.\n" + 
	"#Path announcem.\n" +
	"Path quality\n" +
	"Path adv. round\n" +
	"Last path adv.\n" +
	"Last data\n" +
	"#Reboots\n" +
	"#Routing loops\n" +
	"Battery\n" + 
	"State";
    
    private static final long serialVersionUID = 0;

    HashMap<String,Integer> linkNeigbours;
	HashMap<String,Integer> linkData;

	private JButton connect;

	private JButton open;

	private JButton stop;

    public View(SNIFController controller) {
    	super("SNIF: Sensor Network Inspection Framework");
    	// this.controller = controller;
    	this.controller = controller;
		g = new DirectedSparseGraph();	
		oldDescription = "";
		oldMetrics = "";
		oldTime = "";
    }
    
    public void reset() {
    	g.removeAllVertices();
    	g.removeAllEdges();
        linkNeigbours = new HashMap<String,Integer>();
    	linkData = new HashMap<String,Integer>();
     	speedSlider.setValue(1);
//      	layout.update();
    	vv.repaint();
    }
    
    public void establish() {
    	setDefaultCloseOperation(EXIT_ON_CLOSE);
	    setPreferredSize(new Dimension(1000, 700));
	    layout = new FRLayout(g);
	    PluggableRenderer pr = new PluggableRenderer();
	    // scaler = new CrossoverScalingControl();
	    pr.setEdgeShapeFunction(new EdgeShape.QuadCurve());
	    DefaultModalGraphMouse graphMouse = new DefaultModalGraphMouse();
	    graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
	    vv = new VisualizationViewer(layout, pr);
	    vv.getModel().setRelaxerThreadSleepTime(500);
	    vv.setPickSupport(new ShapePickSupport());
	    vv.setGraphMouse(graphMouse);
        vv.setBackground(Color.WHITE);
        vv.getPickedState().addItemListener(new ItemListener() {
	    	public void itemStateChanged(ItemEvent e) {
	    		updateMetrics();
	    	}
	    });
        getContentPane().add(vv, BorderLayout.CENTER);
	   
        GraphZoomScrollPane zoom = new GraphZoomScrollPane(vv);
        getContentPane().add(zoom, BorderLayout.CENTER);
	               
        // label vertices
        pr.setVertexStringer(new VertexStringer() {
            public String getLabel(ArchetypeVertex v) {
            	if (showVertexLabels) {
        			return v.getUserDatum(vertexKey).toString();
        		}
        		else {
        			return "";
        		}
            }
        });
        // color vertices according to their state
        pr.setVertexPaintFunction(new VertexPaintFunction() {
        	public Paint getFillPaint(Vertex v) {
        		Color c = (Color) v.getUserDatum(statusKey);
        		return c;
        	}
        	public Paint getDrawPaint(Vertex v) {
        		return Color.BLACK;
        	}
        });        
                        
        // label edges
        pr.setEdgeStringer(new EdgeStringer() {
            public String getLabel(ArchetypeEdge e) {
            	Integer packetCount = (Integer) e.getUserDatum(packetCountKey);
            	if (showEdgeLabels && packetCount != 0) {
        			return e.getUserDatum(packetCountKey).toString();
        		}
        		else {
        			return "";
        		}
            }
        });
        
        // adapt thickness of edges according to their packetCount
        pr.setEdgeStrokeFunction(new EdgeStrokeFunction() {
            protected final Stroke thin = new BasicStroke(0);
            protected final Stroke thick = new BasicStroke(3);
            public Stroke getStroke(Edge e) {
                int pc = (Integer) e.getUserDatum(packetCountKey);
                if (pc == 0)
                    return thin;
                else 
                    return thick;
            }
        });
        
        pr.setEdgePaintFunction( new EdgePaintFunction() {
			public Paint getDrawPaint(Edge e) {
				int pc = (Integer) e.getUserDatum(packetCountKey);
				if (pc == 0)
					return Color.black;
				else 
					return Color.blue;
			}

			public Paint getFillPaint(Edge e) {
				return EdgePaintFunction.TRANSPARENT;
			}
        });
        
        connect = new JButton("Connect");
		connect.addActionListener(this);
        
        JButton reorder = new JButton("Reorder");
        reorder.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		layout = new FRLayout(g);
        		vv.setGraphLayout(layout);
        	}
        });
        
        open = new JButton("Open");
		open.addActionListener(this);
        
        stop = new JButton("Stop");
		stop.addActionListener(this);
        stop.setEnabled(false);
        
        speedSlider = new JSlider(JSlider.HORIZONTAL);
        speedSlider.setPreferredSize(new Dimension(130, 50));
        speedSlider.setPaintTicks(true);
        speedSlider.setMinimum(1);
        Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();
        table.put(1, new JLabel("x1"));
        table.put(2, new JLabel("x4"));
        table.put(3, new JLabel("x16"));
        table.put(4, new JLabel("x64"));
        speedSlider.setMaximum(table.size());
        speedSlider.setValue(1);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintLabels(false);
        speedSlider.setSnapToTicks(true);
        speedSlider.setLabelTable(table);
        speedSlider.setPaintLabels(true);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(this);
        speedSlider.setEnabled(false);
        
        textArea = new JTextArea(2, 20);
        timeArea = new JTextArea(1, 5);
                
        JButton help = new JButton("Help");
        help.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		JOptionPane.showMessageDialog((JComponent)e.getSource(), instructions, "Help", JOptionPane.PLAIN_MESSAGE);
        	}
        });
        
        descriptionArea = new JTextArea();
        metricsArea = new JTextArea();
        descriptionArea.setColumns(10);
        metricsArea.setColumns(9);
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, descriptionArea, metricsArea);
        sp.setDividerSize(0);
        JScrollPane scroll = new JScrollPane(sp);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);      
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        JPanel controls = new JPanel();
        JPanel show = new JPanel(new BorderLayout());

        JPanel runPanel = new JPanel(new GridLayout(2,1));
        runPanel.setBorder(BorderFactory.createTitledBorder("Input"));
        runPanel.add(connect);
        runPanel.add(open);
        
        JPanel filePanel = new JPanel(new GridLayout(2,1));
        filePanel.setBorder(BorderFactory.createTitledBorder("Run"));
        filePanel.add(stop);
        filePanel.add(reorder);
       
        JPanel speedPanel = new JPanel(new GridLayout(1,1));
        speedPanel.setBorder(BorderFactory.createTitledBorder("Time Factor"));
        speedPanel.add(speedSlider);
        
        JPanel outputPanel = new JPanel(new GridLayout(1,1));
        outputPanel.setBorder(BorderFactory.createTitledBorder("Output"));
        outputPanel.add(textArea);
        
        JPanel timePanel = new JPanel(new GridLayout(1,1));
        timePanel.setBorder(BorderFactory.createTitledBorder("Time"));
        timePanel.add(timeArea);
        
        JPanel helpPanel = new JPanel(new GridLayout(1,1));
        helpPanel.setBorder(BorderFactory.createTitledBorder("Help"));
        helpPanel.add(help);
        
        JPanel showMetricsPanel = new JPanel(new GridLayout(1,1));
        showMetricsPanel.setBorder(BorderFactory.createTitledBorder("Metrics"));
        showMetricsPanel.add(scroll);
        
        controls.add(runPanel);
        controls.add(filePanel);
        controls.add(speedPanel);
        controls.add(outputPanel);
        controls.add(timePanel);
        controls.add(helpPanel);
        show.add(showMetricsPanel);
        getContentPane().add(controls, BorderLayout.SOUTH);
        getContentPane().add(show, BorderLayout.EAST);
        
        addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent event) {
				KeyStroke k = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiers());
				KeyStroke ctrlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_MASK);
				if (k == ctrlA) {
					try {
						PickedState pickedState = vv.getPickedState();
						Iterator it = g.getVertices().iterator();
				        while (it.hasNext()) {
				        	pickedState.pick((ArchetypeVertex) it.next(), true);
				        }
					}
					catch (ConcurrentModificationException ex) {
					}
			    }
			}
		});
               
        pack();
		setVisible(true);
		setExtendedState(MAXIMIZED_BOTH);	
		requestFocusInWindow();
    }
    
	public void updateMetrics() {
    	description = "";
    	metrics = "";
    	try {
	    	Iterator it = vv.getPickedState().getPickedVertices().iterator();
	    	while (it.hasNext()) {
				Vertex vertex = (Vertex) it.next();
				String m = (String) vertex.getUserDatum(metricsKey);
				if (m != null) {
		    		description += d + "\n\n";
		    		metrics     += m + "\n\n";
				} else {
					description +=  "none\n";
					metrics     +=  "none\n";
				}
			}
    	}
    	catch (ConcurrentModificationException ex) {
    	}
    	
		if (!oldDescription.equals(description)) {
			descriptionArea.setText(description);
			descriptionArea.setText(description);
			descriptionArea.update(descriptionArea.getGraphics());
			oldDescription = description;
		}
		if (!oldMetrics.equals(metrics)) {
			metricsArea.setText(metrics);
			metricsArea.setText(metrics);
			metricsArea.update(metricsArea.getGraphics());
			oldMetrics = metrics;
    	}
    }
    
    public void writeMessage(String s) {
    	textArea.setText(s);
		textArea.update(textArea.getGraphics());
	}
    
    
    /** used helper methods */


    private Vertex getNodeVertex(String name) {
    	Iterator it = g.getVertices().iterator();
    	Vertex v;
    	while (it.hasNext()) {
    		v = (Vertex) it.next();
    		if (v.getUserDatum(vertexKey).equals(name)) {
    			return v;
    		}
    	}
    	return null;
    }
    
    private Edge getLinkEdge(String name) {
    	Iterator it = g.getEdges().iterator();
    	Edge e;
    	while (it.hasNext()) {
			e = (Edge) it.next();
			if (e.getUserDatum(edgeKey).equals(name)) {
				return e;
			}
		}
    	return null;
    }
 
    
    /** interface used by data stream graph */

    /** 
     * set virtual time 
     **/
    public void setTime(String time) {
    	if (!time.equals(oldTime)) {
	    	timeArea.setText(time);
			timeArea.update(timeArea.getGraphics()); 
			oldTime = time;
    	}
    }
    
    /**
     * evidence that a node exists was gathered is directly or indirectly
     * a packet was sent by this node
     * a packet was sent to this node
     * a packet lists this node */
    public  void nodeSeen( int address) {
    	String addr = "" + address;
    	if (address == 65535) return;
    	if (address == 65536) return;
    	
    	// add node. node's won't disappear
    	Vertex node = getNodeVertex( addr);
    	if (node == null) {
    		// add node
	      	node = new DirectedSparseVertex();
    	   	node.addUserDatum(vertexKey, addr, UserData.CLONE);
	    	node.addUserDatum(statusKey, Color.GRAY, UserData.CLONE);
	    	g.addVertex(node);
	    	if (haveCoordinates){
		    	Object coordKey = layout.getBaseKey();
				Coordinates coords = nodeCoordinates.get( address );
	    		if (coords != null) {
					layout.lockVertex(node);
		    		node.addUserDatum( coordKey, coords, UserData.SHARED);
	    		} else {
	    			System.out.println("View.nodeSeen("+address+"), but not in nodeCoordinates");
	    		}
	    	}
	    	((FRLayout) layout).update();
	    	vv.repaint();
        	writeMessage("Node "+addr+" added");
    	}
    }


    /**
     * node state changes
     * @param args
     */
    public void setNodeState( int address, Color color ) {
    	// add node. nodes won't disappear
    	String addr = "" + address;
    	// check that node exists
    	nodeSeen( address );
    	Vertex node = getNodeVertex( addr);
    	if (node != null) {
    		node.setUserDatum(statusKey, color, UserData.CLONE);
        	vv.repaint();
        	writeMessage("Node "+addr+" state changed");
    	}
    }
    
    /**
     * DURING A SPECIFIC PERIOD, THIS AMOUNT OF 
     * 
     * @PARAM FROM
     * @PARAM TO
     */

    public void setLinkNeigbours(int from, int to, int reports) {
		// String otherDirection = "" + to + "#" + from;
		// linkNeigbours.put( otherDirection, reports );

		String edgeName = ""+from+"#"+to;
		Edge link = getLinkEdge( edgeName );
		if (reports > 0) {
			// assert link exists
			if (link == null) {
				nodeSeen( from );
				nodeSeen( to );
				Vertex fromV = getNodeVertex( ""+from);
				Vertex toV = getNodeVertex( ""+to);
		    	link = new DirectedSparseEdge(fromV, toV);
	    	  	// add key for look-up
		    	link.addUserDatum(edgeKey, edgeName, UserData.SHARED);
		     	// add packet count to edge
		    	link.addUserDatum(packetCountKey, 0, UserData.SHARED);
		    	// set neighbour reports
		    	link.addUserDatum(neighboutSeenKey, reports, UserData.SHARED);
		    	// finally put it on the hashtable
		    	g.addEdge(link);
	        	writeMessage("Node "+from+" lists new neighbor "+to);
			}
		} else {
			// assert link is removed
			if (link != null) {
	        	writeMessage("Node "+to+" vanishes from node "+from+"'s neighbor list");
				g.removeEdge(link);
			}
				
		}
	}

    /**
     * DURING A SPECIFIC PERIOD, THIS AMOUNT OF PACKETS HAVE BEEN SENT FROM ONE NODE TO THE OTHER
     * 
     * @PARAM FROM
     * @PARAM TO
     */

  public void setLinkData(int from, int to, int reports) {
		// String otherDirection = "" + to + "#" + from;
		// linkData.put( otherDirection, reports );

	  	String edgeName = ""+from+"#"+to;
		Edge link = getLinkEdge( edgeName );

		// assert link exists
		if (link == null) {
			nodeSeen( from );
			nodeSeen( to );
			Vertex fromV = getNodeVertex( ""+from);
			Vertex toV = getNodeVertex( ""+to);
			link = new DirectedSparseEdge(fromV, toV);
			// add key for look-up
			link.addUserDatum(edgeKey, edgeName, UserData.SHARED);
			// finally put it on the hashtable
			g.addEdge(link);
			writeMessage("Node "+from+" sends first data to "+to);
		}
		link.setUserDatum( packetCountKey, reports, UserData.SHARED);
//		layout.update();
    	vv.repaint();
  }

  	
	/**
     * set node metrics
     * @param args
     */
    public void setNodeMetrics( int address, String metricInfo) {
      	String addr = "" + address;
    	Vertex node = getNodeVertex( addr);
    	if (node != null) {
    		node.setUserDatum(metricsKey, metricInfo, UserData.SHARED);
    	}
    	updateMetrics();
    }

	public void actionPerformed(ActionEvent arg0) {
		// TODO Auto-generated method stub
		System.out.println(arg0);
		if (arg0.getActionCommand().equals("Connect")) {
			controller.useDSN = true;
			controller.useLog = false;
			connect.setEnabled(false);
			open.setEnabled(false);
	        speedSlider.setEnabled(false);
			reset();
			synchronized(controller.start) {;
			
				controller.start.notify();
			}
			stop.setEnabled(true);
		} else if (arg0.getActionCommand().equals("Open")) {
			controller.useDSN = false;
			controller.useLog = true;
			FileDialog fd = new FileDialog(this, "Open SNIF Log File...");
			fd.setDirectory( new File(".").getAbsolutePath());
			fd.setFilenameFilter(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith("log_");
				}
			});
			fd.setVisible(true);
			if (fd.getFile() != null) {
				connect.setEnabled(false);
				open.setEnabled(false);
				reset();
				controller.PACKET_INPUT = fd.getDirectory()+File.separator+fd.getFile();
				synchronized(controller.start) {
					controller.start.notify();
				}
				stop.setEnabled(true);
		        speedSlider.setEnabled(true);
			}
		} else if (arg0.getActionCommand().equals("Stop")) {
			Scheduler.stop();
	        speedSlider.setEnabled(false);
		}
	}

	public void simulationStopped() {
		connect.setEnabled(true);
		open.setEnabled(true);
		stop.setEnabled(false);
	}
	
	public void setBTConnection(String dsnNode) {
		if (dsnNode != null) {
			writeMessage( "Connected to DSN at "+dsnNode);
		} else {
			connect.setEnabled(false);
		}
	}
	
	public void stateChanged(ChangeEvent e) {
		JSlider source = (JSlider) e.getSource();
		if (!source.getValueIsAdjusting()) {
			Scheduler.speed = (float) Math.pow( 4, source.getValue() -1 );
		}
	}

	public void setNodeCoordinates(HashMap<Integer, Coordinates> nodeCoordinates) {
		this.nodeCoordinates = nodeCoordinates;
		this.haveCoordinates = true;
	}
}