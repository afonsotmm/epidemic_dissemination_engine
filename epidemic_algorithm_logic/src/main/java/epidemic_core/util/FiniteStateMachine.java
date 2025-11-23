package epidemic_core.util;

import java.time.Duration;
import java.time.Instant;

public class FiniteStateMachine<T extends Enum<T>> {

    private T state;
    private T newState;
    private T prevState;

    private Instant tes;
    private Duration tis;
    private boolean done;

    public FiniteStateMachine(T initialState) {
        this.state = initialState;
        this.newState = initialState;
        this.prevState = initialState;
        this.tes = Instant.now();
        this.tis = Duration.between(this.tes, Instant.now());
        this.done = false;

        System.out.println("Fsm instance created.");
    }

    public void resetTimes() {
        tes = Instant.now();
        tis = Duration.ZERO;
    }

    public void setState() {
        if(this.state != this.newState) {
            this.prevState = this.state;
            this.state = this.newState;
            this.resetTimes();
        }
    }

    public void updateTis() { tis = Duration.between(tes, Instant.now()); }

    public boolean checkTimeout(double timeoutSeconds) { return tis.toMillis()/1000.0 > timeoutSeconds; }

    // Setters
    public void setNewState(T newState) {
        this.newState = newState;
    }

    public void setPrevState(T prevState) { this.prevState = prevState; }

    public void setDone(boolean done) { this.done = done; }

    // Getters
    public T getState() {
        return state;
    }

    public T getPrevState() {
        return prevState;
    }

    public boolean getDone() { return done; }

    public double getTisSeconds() {
        return tis.toMillis() / 1000.0;
    }
}
