package logic;

public class SubjectState {

    private final String subject;
    private Integer timeStamp;
    private String currValue;
    private String status;

    // Constructor
    public SubjectState(String subject, Integer timeStamp, String currValue, String status) {
        this.subject = subject;
        this.timeStamp = timeStamp;
        this.currValue = currValue;
        this.status = status;
    }

    // Getters
    public String getSubject() { return subject; }
    public Integer getTimeStamp() { return timeStamp; }
    public String getCurrValue() { return currValue; }
    public String getStatus() { return status; }

    // Setters
    public void setTimeStamp(Integer timeStamp) { this.timeStamp = timeStamp; }
    public void setCurrValue(String currValue) { this.currValue = currValue; }
    public void setStatus(String status) { this.status = status; }

}
