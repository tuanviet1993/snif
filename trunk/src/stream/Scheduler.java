package stream;

import java.util.Random;
import java.util.TreeMap;

import stream.tuple.PacketTuple;

/**
 * Main processing loop
 * The scheduler continously polls the given source, pushes its data and
 * also handles Timer requests
 *  
 * @author mringwal
 */
public class Scheduler {
	
	class TimerCallback {
		long timestamp;
		TimeTriggered callee;
		TimerCallback next = null;
		
		/**
		 * @param timestamp
		 * @param callee
		 */
		public TimerCallback(long timestamp, TimeTriggered callee) {
			this.timestamp = timestamp;
			this.callee = callee;
		}
	}
	private TreeMap<Long,TimerCallback> timers = new TreeMap<Long,TimerCallback>();

	private static TimeTriggered clockCallback = null; 
	
	private static Scheduler instance = null;

	public static float packetloss = -1; // no loss

	public static float speed = 1;
	
	private static boolean stop = false;
	
	public static Scheduler getInstance() {
		if (instance == null) {
			instance = new Scheduler();
		}
		return instance;
	}
	
	public static void registerClockView(TimeTriggered callee) {
		clockCallback = callee;
	}
	
	public void registerTimeout( long timeout, TimeTriggered callee) {
		TimerCallback oldT = timers.get( timeout );
		TimerCallback newT = new TimerCallback( timeout, callee);
		// keep link to previous entry
		if (oldT != null) {
			newT.next = oldT;
		}
		timers.put( timeout, newT);
	}
	
	/**
	 * invoke timerHandler on registered timeouts
	 * 
	 * @param timestamp
	 */
	public void processTimers( long timestamp) {
		while ( !timers.isEmpty() && timers.firstKey() < timestamp){
			long timeout = timers.firstKey();
			TimerCallback next = timers.get(timeout);
			while (next != null){
				next.callee.handleTimerEvent(timestamp);
				next = next.next;
			}
			timers.remove( timeout );
		}
	}
	
	/**
	 * Execute 
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static void run(AbstractSource<? extends ITimeStampedObject> source) {

		// reset all data
		instance = null;
		
		ITimeStampedObject packet;
		AbstractSource<ITimeStampedObject> src2 = (AbstractSource<ITimeStampedObject>) source;
		long start = System.currentTimeMillis();
		int packetCounter = 0;

		if (source instanceof RealTime) {
			RealTime realTimeSrc = (RealTime) source;
			while (!stop) {
				if (realTimeSrc.ready()) {
					packet = src2.next();

					packetCounter++;
					
					// process timeouts
					long timestamp = packet.getTime();
					Scheduler.getInstance().processTimers(timestamp);

					// update clock
					if (clockCallback != null) {
						clockCallback.handleTimerEvent( timestamp );
					}
					// simulate packet loss..
					Random random = new Random();
					if ( random.nextFloat() < packetloss) continue;
		
					// process packet
					if ( ((PacketTuple) packet).getPacket() != null) {
						src2.transfer(packet, timestamp);
					}
				} else {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} else {
			long simulationTime = 0;
			while (( packet = (ITimeStampedObject) src2.next()) != null && !stop) {
				packetCounter++;
				
				// simulate packet loss..
				Random random = new Random();
				if ( random.nextFloat() < packetloss) continue;

				// process timeouts
				long timestamp = packet.getTime();

				// wait for packet time, if not batch processing
				if (speed >= 0) {
					int sleepTime = 20;
					while (simulationTime < timestamp) {
						try {
							Thread.sleep(20);
							simulationTime += sleepTime * speed;

							// update clock
							if (clockCallback != null) {
								clockCallback.handleTimerEvent( simulationTime );
							}
							
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				
				Scheduler.getInstance().processTimers(timestamp);

				// update clock
				if (clockCallback != null) {
					clockCallback.handleTimerEvent( timestamp );
				}
	
				// process packet
				src2.transfer(packet, timestamp);
			}
		}
		stop = false;
		long end = System.currentTimeMillis();
		System.out.println("Duration: " + ((end - start) / 1000) + " s. "
				+ packetCounter + " packets");
	}

	public static void stop() {
		// TODO Auto-generated method stub
		stop = true;
	}
}
