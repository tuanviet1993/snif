/**
 * 
 */
package stream.tuple;

import java.util.HashMap;

public abstract class TreePredicate{
	public abstract boolean invoke( HashMap<Object,Tuple> input);
	public abstract boolean canEvalute( HashMap<Object,Tuple> input);
}