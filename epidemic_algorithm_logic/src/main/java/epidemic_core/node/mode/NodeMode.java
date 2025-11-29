package epidemic_core.node.mode;

public enum NodeMode
{
    PULL,
    PUSH,
    PUSHPULL;

    public static NodeMode fromString(String input){

        return switch(input.toLowerCase()){
            case "pull" -> PULL;
            case "push" -> PUSH;
            case "pushpull" -> PUSHPULL;

            default -> throw new IllegalStateException("Unexpected value: " + input.toLowerCase());
        };
    }
}
