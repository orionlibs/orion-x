package com.orion.quantumcomputing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class QuantumStep
{
    private final QuantumStepType type;
    private final ArrayList<QuantumGate> gates = new ArrayList<>();
    private final String name;
    private int index;
    private QuantumProgram program;
    private int complexStep = -1; // if a complex QuantumStep needs to be broken into
    // simple QuantumSteps, only one simple QuantumStep can have this value to be the index of the complex QuantumStep
    private boolean informal = false;


    public QuantumStep(QuantumGate... moreGates)
    {
        this("unknown", moreGates);
    }


    public QuantumStep(String name, QuantumGate... gates)
    {
        this.name = name;
        addGates(gates);
        this.type = QuantumStepType.NORMAL;
    }


    public QuantumStep(QuantumStepType type)
    {
        this.type = type;
        this.name = "pseudo";
    }


    public QuantumStepType getType()
    {
        return this.type;
    }


    public String getName()
    {
        return this.name;
    }


    public void addGate(QuantumGate gate) throws IllegalArgumentException
    {
        verifyUnique(Objects.requireNonNull(gate));
        gates.add(gate);
    }


    public void addGates(QuantumGate... moreGates) throws IllegalArgumentException
    {
        for(QuantumGate g : moreGates)
        {
            addGate(g);
        }
    }


    public List<QuantumGate> getGates()
    {
        return Collections.unmodifiableList(gates);
    }


    public void removeGate(QuantumGate g)
    {
        gates.remove(g);
    }


    public int getComplexStep()
    {
        return this.complexStep;
    }


    public void setComplexStep(int idx)
    {
        this.complexStep = idx;
    }


    public void setInformalStep(boolean b)
    {
        this.informal = b;
    }


    public boolean isInformal()
    {
        return informal;
    }


    public int getIndex()
    {
        return this.index;
    }


    public void setIndex(int s)
    {
        this.index = s;
        this.complexStep = s;
    }


    public QuantumProgram getProgram()
    {
        return this.program;
    }


    public void setProgram(QuantumProgram p)
    {
        this.program = p;
    }


    public void setInverse(boolean val)
    {
        for(QuantumGate g : gates)
        {
            if(g instanceof BlockGate)
            {
                ((BlockGate)g).inverse();
            }
            else
            {
                g.setInverse(val);
            }
        }
    }


    private void verifyUnique(QuantumGate gate)
    {
        for(QuantumGate g : gates)
        {
            long overlap = g.getAffectedQubitIndexes()
                            .stream()
                            .filter(gate.getAffectedQubitIndexes()::contains)
                            .count();
            if(overlap > 0)
            {
                throw new IllegalArgumentException("Adding gate that affects a qubit already involved in this QuantumStep");
            }
        }
    }


    @Override
    public String toString()
    {
        if(this.getType() == QuantumStepType.PSEUDO)
        {
            return "Pseudo-step";
        }
        else
        {
            return "Step with gates " + this.gates;
        }
    }
}
