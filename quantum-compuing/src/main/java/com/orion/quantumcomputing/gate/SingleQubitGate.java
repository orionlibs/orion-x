package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;
import com.orion.quantumcomputing.QuantumGate;
import java.util.Collections;
import java.util.List;

public abstract class SingleQubitGate implements QuantumGate
{
    protected int idx;
    private boolean inverse;


    public SingleQubitGate()
    {
    }


    public SingleQubitGate(int idx)
    {
        this.idx = idx;
    }


    @Override
    public int getMainQubitIndex()
    {
        return this.idx;
    }


    @Override
    public void setMainQubitIndex(int idx)
    {
        this.idx = idx;
    }


    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        throw new RuntimeException("A SingleQubitGate can not have additional qubits");
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return Collections.singletonList(idx);
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return idx;
    }


    @Override
    public String getName()
    {
        return this.getClass().getName();
    }


    @Override
    public String getCaption()
    {
        return getName();
    }


    @Override
    public String getGroup()
    {
        return "SingleQubit";
    }


    @Override
    public int getSize()
    {
        return 1;
    }


    @Override
    public abstract Complex[][] getMatrix();


    @Override
    public void setInverse(boolean v)
    {
        this.inverse = v;
    }


    @Override
    public String toString()
    {
        return "Gate with index " + idx + " and caption " + getCaption();
    }
}
