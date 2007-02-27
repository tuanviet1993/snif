package gui;

/**
 * Delegate class for SNIF applications which use the provided gui.VIEW
 *  
 * @author mringwal
 *
 */
public abstract class SNIFController {

	protected boolean useLog = false;
	protected boolean useDSN = false;
	protected Object start = null;
	protected String PACKET_INPUT = null;
}
