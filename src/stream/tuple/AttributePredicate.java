package stream.tuple;

import stream.Predicate;

public class AttributePredicate extends Predicate<Tuple> {

	/**
	 * @param attributeName
	 * @param attributeValue
	 */
	public AttributePredicate(String attributeName, Object attributeValue) {
		this.attribute = Tuple.getAttributeId( attributeName);
		this.attributeValue = attributeValue;
		// System.out.println("PacketAttributePredicate: "+attributeName + " == " + attributeValue);
	}
	
	@Override
	public boolean invoke(Tuple packet, long timestamp) {
		// System.out.println("PacketAttributePredicate: "+attributeName + ": " + packet.getAttribute(attributeName) 
		//		+" == " + attributeValue);
		return packet.getAttribute(attribute).equals(attributeValue);
	}
	
	int attribute;
	Object attributeValue;
}
