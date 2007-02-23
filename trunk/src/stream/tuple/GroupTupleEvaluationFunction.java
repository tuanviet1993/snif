package stream.tuple;

import java.util.HashMap;

import stream.GroupEvaluationFunction;

public interface GroupTupleEvaluationFunction<I> extends GroupEvaluationFunction<I,Object,Object,Tuple> {
	public Tuple process(Object id, HashMap<Object,Tuple> input);
}
