package com.orion.quantumcomputing.gate;

import Complex;
import java.util.Collections;
import java.util.List;
import org.redfx.strange.Gate;

/**
 *
 * This class describe a Gate that operates on a single qubit only.
 *
 * @author johan
 * @version $Id: $Id
 */
public abstract class SingleQubitGate implements Gate
{
    protected int idx;
    private boolean inverse;


    /**
     * <p>Constructor for SingleQubitGate.</p>
     */
    public SingleQubitGate()
    {
    }


    /**
     * <p>Constructor for SingleQubitGate.</p>
     *
     * @param idx a int
     */
    public SingleQubitGate(int idx)
    {
        this.idx = idx;
    }


    /** {@inheritDoc} */
    @Override
    public int getMainQubitIndex()
    {
        return this.idx;
    }


    /** {@inheritDoc} */
    @Override
    public void setMainQubitIndex(int idx)
    {
        this.idx = idx;
    }


    /** {@inheritDoc} */
    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        throw new RuntimeException("A SingleQubitGate can not have additional qubits");
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return Collections.singletonList(idx);
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return idx;
    }


    /** {@inheritDoc} */
    @Override
    public String getName()
    {
        return this.getClass().getName();
    }


    /** {@inheritDoc} */
    @Override
    public String getCaption()
    {
        return getName();
    }


    /** {@inheritDoc} */
    @Override
    public String getGroup()
    {
        return "SingleQubit";
    }


    /** {@inheritDoc} */
    @Override
    public int getSize()
    {
        return 1;
    }


    /** {@inheritDoc} */
    @Override
    public abstract Complex[][] getMatrix();


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean v)
    {
        this.inverse = v;
    }


    /** {@inheritDoc} */
    @Override public String toString()
    {
        return "Gate with index " + idx + " and caption " + getCaption();
    }
}
