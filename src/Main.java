import java.io.File;
import java.util.regex.Pattern;

import model.Packet;
import model.PacketTracer;


/**
 * @author mringwal
 * 
 */
public class Main {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length > 1) {
			Packet.dumpPackets = true;
			PacketTracer.dump  = true;
		}
		boolean foundDSNFolder = false;
		if (args.length > 0) {
			File dir = new File(args[0]);
			if (dir.exists()) {
				File[] children = dir.listFiles();
				for (File f : children) {
					// System.out.println("Testing.." + f);
					if (dsnFilter.matcher(f.getName()).matches()) {
						// System.out.println("Matched.." + f);
						foundDSNFolder = true;
						// processing all sub-folders
						args[0] = f.getAbsolutePath();
						DetectionAlgoTuple.main(args);
						System.out.println();
					}
				}
				if (!foundDSNFolder) DetectionAlgoTuple.main(args);
			}
		} else {
			DetectionAlgoTuple.main(args);
		}
	}

	/** pattern to identify DSN Logs*/
	static Pattern dsnFilter = Pattern.compile("group.*.\\.sim");
}
