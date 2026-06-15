package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.QuantumGate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class ThreeQubitGate implements QuantumGate
{
    private int first;
    private int second;
    private int third;


    public ThreeQubitGate()
    {
    }


    public ThreeQubitGate(int first, int second, int third)
    {
        this.first = first;
        this.second = second;
        this.third = third;
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
        if((cnt < 1) || (cnt > 2))
        {
            System.err.println("Can't set qubit " + cnt + " as additional on a three qubit gate");
        }
        if(cnt == 1)
        {
            this.second = idx;
        }
        if(cnt == 2)
        {
            this.third = idx;
        }
    }


    public int getMainQubit()
    {
        return this.first;
    }


    public int getSecondQubit()
    {
        return this.second;
    }


    public int getThirdQubit()
    {
        return this.third;
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return Arrays.asList(first, second, third);
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(getAffectedQubitIndexes());
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
        return "ThreeQubit";
    }


    @Override
    public String toString()
    {
        return "Gate acting on qubits " + first + ", " + second + " and " + third + " and caption " + getCaption();
    }
}
