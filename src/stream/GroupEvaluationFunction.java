package stream;

import java.util.HashMap;

public interface GroupEvaluationFunction<I,J,K,O> {
	public O process(K gID, HashMap<J,I> input);
}
