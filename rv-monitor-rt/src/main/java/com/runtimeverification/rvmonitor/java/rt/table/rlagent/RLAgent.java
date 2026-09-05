package com.runtimeverification.rvmonitor.java.rt.table.rlagent;

import com.runtimeverification.rvmonitor.java.rt.tablebase.AbstractMonitor;
import java.lang.Math;
import java.util.Random;
import java.util.HashSet;
import java.util.HashMap;

public class RLAgent {
    private static final class ContextState {
        private double Qn;
        private double Qc;
        private double reward;

        private int numTotTraces;
        private int numDupTraces;
        private int timeStep;

        private boolean converged;
        private boolean convStatus;
        private AbstractMonitor monitor;

        private ContextState(double initc, double initn) {
            this.Qc = initc;
            this.Qn = initn;
        }
    }

    private double EPSILON;
    private double ALPHA; 
    private double AUDIT_RATE = 0.05;

    private HashSet<Integer> uniqueTraces; 
    private double THRESHOLD;
    private double initc;
    private double initn;

    private HashMap<Integer, ContextState> contextStates = new HashMap<Integer, ContextState>();
    private ContextState activeContext;

    public RLAgent(HashSet<Integer> uniqueTraces, 
	double alpha, double epsilon, double threshold, double initc, double initn) {
        this.uniqueTraces = uniqueTraces;

	this.ALPHA = alpha;
	this.EPSILON = epsilon;
	this.THRESHOLD = threshold;

	this.initc = initc;
	this.initn = initn;
    }

    private int contextHash(int eventHash, int typeHash, int sourceHash) {
        int hash = 17;
        hash = 31 * hash + eventHash;
        hash = 31 * hash + typeHash;
        hash = 31 * hash + sourceHash;
        return hash;
    }

    private ContextState getContextState(int contextHash) {
        ContextState state = contextStates.get(contextHash);
        if (state == null) {
            state = new ContextState(initc, initn);
            contextStates.put(contextHash, state);
        }
        return state;
    }

    private void checkConverged(ContextState state) {
	if (Math.abs(1.0 - Math.abs(state.Qc - state.Qn)) < THRESHOLD) {
	    state.converged = true;
	    state.convStatus = (state.Qn < state.Qc) ? true : false;
	}
    }

    private void update(ContextState state) {
        if (state == null || state.converged) {
            return;
        }
	if (state.monitor != null) {
	    state.numTotTraces++;
	    if (!uniqueTraces.contains(state.monitor.traceVal)) {
		uniqueTraces.add(state.monitor.traceVal);
	        state.reward = 1.0;
	    } else {
		state.numDupTraces++;
	        state.reward = 0.0;
	    }
	    state.Qc = state.Qc + ALPHA * (state.reward - state.Qc);
	} else {
	    state.reward = (double)state.numDupTraces / state.numTotTraces;
	    state.Qn = state.Qn + ALPHA * (state.reward - state.Qn);
        }
	checkConverged(state);
    }

    public boolean decideAction(int eventHash, int typeHash, int sourceHash) {
        update(activeContext);

        ContextState state = getContextState(contextHash(eventHash, typeHash, sourceHash));
        activeContext = state;
	boolean create;
	// Initial Action Selection
	if (state.timeStep++ == 0) {
            create = true;
	} else if (state.converged) {
	    create = state.convStatus;
	} else if (Math.random() < EPSILON) {
	    // Exploration Phase
            Random random = new Random();
	    create = random.nextBoolean();
        } else {
	    // Exploitation Phase
            create = (state.Qn <= state.Qc);
        }

	// Persistent random auditing for skipped monitor creations.
        if (!create && Math.random() < AUDIT_RATE) {
            create = true;
        }
        return create;
    }

    public void setMonitor(AbstractMonitor monitor) {
	if (activeContext == null) {
	    return;
	}
	activeContext.monitor = monitor;
	if (activeContext.converged) {
	    monitor.recordEvents = false;
	}
    }

    public void clearMonitor() {
	if (activeContext != null) {
	    activeContext.monitor = null;
	}
    }
}
