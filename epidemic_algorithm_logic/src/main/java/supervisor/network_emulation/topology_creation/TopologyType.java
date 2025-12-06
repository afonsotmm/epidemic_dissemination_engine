package supervisor.network_emulation.topology_creation;

public enum TopologyType
{
    FULL_MESH,
    PARTIAL_MESH,
    RING,
    STAR;

    public static TopologyType fromString(String input){

        return switch(input.toLowerCase()){
            case "full mesh" -> FULL_MESH;
            case "partial mesh" -> PARTIAL_MESH;
            case "ring" -> RING;
            case "star" -> STAR;

            default -> throw new IllegalStateException("Unexpected value: " + input.toLowerCase());
        };
    }
}
