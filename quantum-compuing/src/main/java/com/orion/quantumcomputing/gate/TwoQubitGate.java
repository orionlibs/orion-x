package com.orion.quantumcomputing.gate;

import java.util.Arrays;
import java.util.List;

public abstract class TwoQubitGate implements Gate
{
    private int first;
    private int second;
    private int highest = -1;
    private boolean inverse;


    public TwoQubitGate()
    {
    }


    public TwoQubitGate(int first, int second)
    {
        this.first = first;
        this.second = second;
        this.highest = Math.max(first, second);
    }


    @Override
    public int getMainQubitIndex()
    {
        return this.first;
    }


    @Override
    public void setMainQubitIndex(int idx)
    {
        this.first = idx;
    }


    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        this.second = idx;
    }


    public int getSecondQubitIndex()
    {
        return this.second;
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return Arrays.asList(first, second);
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return highest;
    }


    public void setHighestAffectedQubitIndex(int v)
    {
        this.highest = v;
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
        return "TwoQubit";
    }


    @Override
    public int getSize()
    {
        return 2;
    }


    @Override
    public void setInverse(boolean v)
    {
        this.inverse = v;
    }


    @Override
    public String toString()
    {
        return "Gate acting on qubits " + first + " and " + second + " and caption " + getCaption();
    }
}
