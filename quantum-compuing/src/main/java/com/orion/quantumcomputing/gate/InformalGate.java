package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;
import com.orion.quantumcomputing.QuantumGate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public abstract class InformalGate implements QuantumGate
{
    private List<Integer> affected = new LinkedList<>();
    private int mainIndex;


    public InformalGate()
    {
        this(0);
    }


    public InformalGate(int idx)
    {
        this.mainIndex = idx;
        affected.add(idx);
    }


    @Override
    public int getMainQubitIndex()
    {
        return 0;
    }


    @Override
    public void setMainQubitIndex(int idx)
    {
    }


    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(affected);
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return affected;
    }


    @Override
    public String getGroup()
    {
        return "informal";
    }


    @Override
    public Complex[][] getMatrix()
    {
        return new Complex[0][];
    }
}
