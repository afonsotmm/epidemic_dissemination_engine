package supervisor.network_emulation.neighbors_and_subject;

import java.util.List;
import java.util.Map;

public record StructuralInfosMatrix(
    Map<Integer, List<Integer>> adjMap,
    Map<Integer, String> nodeIdToSubject
)
{ }
