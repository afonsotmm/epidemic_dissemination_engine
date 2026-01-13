package supervisor.network_emulation.neighbors_and_subject;

import java.util.List;

public record NodeStructuralInfo(Integer nodeId, List<Integer> neighbors, String subject) {

    public boolean isSourceNode() {
        return subject != null;
    }
}
