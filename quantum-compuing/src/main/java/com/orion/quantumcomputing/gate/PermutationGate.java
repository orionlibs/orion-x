package com.orion.quantumcomputing.gate;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class PermutationGate implements QuantumGate
{
    private int a;
    private int b;
    private int n;
    private Complex[][] m;
    private List<Integer> affected = new LinkedList<>();


    public PermutationGate(int a, int b, int n)
    {
        assert (a < n);
        assert (b < n);
        this.a = a;
        this.b = b;
        this.n = n;
        for(int i = 0; i < n; i++)
        {
            affected.add(i);
        }
    }


    @Override
    public int getMainQubitIndex()
    {
        return this.a;
    }


    @Override
    public void setMainQubitIndex(int idx)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    public int getIndex1()
    {
        return a;
    }


    public int getIndex2()
    {
        return b;
    }


    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return affected;
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(affected);
    }


    @Override
    public String getCaption()
    {
        return "P";
    }


    @Override
    public String getName()
    {
        return "permutation gate";
    }


    @Override
    public String getGroup()
    {
        return "permutation";
    }


    @Override
    public Complex[][] getMatrix()
    {
        throw new RuntimeException("No matrix required for Permutation");
    }


    @Override
    public int getSize()
    {
        return 2;
    }


    @Override
    public void setInverse(boolean v)
    {
        // NOP
    }


    @Override
    public String toString()
    {
        return "Perm " + a + ", " + b;
    }
}
