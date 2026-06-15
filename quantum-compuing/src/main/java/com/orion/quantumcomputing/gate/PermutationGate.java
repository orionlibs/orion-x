package com.orion.quantumcomputing.gate;

import Complex;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.redfx.strange.Gate;

/**
 * <p>PermutationGate class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class PermutationGate implements Gate
{
    private int a;
    private int b;
    private int n;
    private Complex[][] m;
    private List<Integer> affected = new LinkedList<>();


    /**
     * <p>Constructor for PermutationGate.</p>
     *
     * @param a a int
     * @param b a int
     * @param n a int
     */
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


    /** {@inheritDoc} */
    @Override
    public int getMainQubitIndex()
    {
        return this.a;
    }


    /** {@inheritDoc} */
    @Override
    public void setMainQubitIndex(int idx)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    /**
     * <p>getIndex1.</p>
     *
     * @return a int
     */
    public int getIndex1()
    {
        return a;
    }


    /**
     * <p>getIndex2.</p>
     *
     * @return a int
     */
    public int getIndex2()
    {
        return b;
    }


    /** {@inheritDoc} */
    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return affected;
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(affected);
    }


    /** {@inheritDoc} */
    @Override
    public String getCaption()
    {
        return "P";
    }


    /** {@inheritDoc} */
    @Override
    public String getName()
    {
        return "permutation gate";
    }


    /** {@inheritDoc} */
    @Override
    public String getGroup()
    {
        return "permutation";
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        throw new RuntimeException("No matrix required for Permutation");
        //        return m;
    }


    /** {@inheritDoc} */
    @Override
    public int getSize()
    {
        return 2;
    }


    /** {@inheritDoc} */
    @Override public void setInverse(boolean v)
    {
        // NOP
    }


    /** {@inheritDoc} */
    @Override
    public String toString()
    {
        return "Perm " + a + ", " + b;
    }
}
