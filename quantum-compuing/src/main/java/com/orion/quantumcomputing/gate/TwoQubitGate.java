package com.orion.quantumcomputing.gate;

import java.util.Arrays;
import java.util.List;
import org.redfx.strange.Gate;

/**
 *
 * This class describe a Gate that operates on two qubits. In a single
 * <code>Step</code>, there should not be two Gates that act on the same qubit.
 *
 * @author johan
 * @version $Id: $Id
 */
public abstract class TwoQubitGate implements Gate
{
    private int first;
    private int second;
    private int highest = -1;
    private boolean inverse;


    /**
     * <p>Constructor for TwoQubitGate.</p>
     */
    public TwoQubitGate()
    {
    }


    /**
     * <p>Constructor for TwoQubitGate.</p>
     *
     * @param first a int
     * @param second a int
     */
    public TwoQubitGate(int first, int second)
    {
        this.first = first;
        this.second = second;
        this.highest = Math.max(first, second);
    }


    /** {@inheritDoc} */
    @Override
    public int getMainQubitIndex()
    {
        return this.first;
    }


    /** {@inheritDoc} */
    @Override
    public void setMainQubitIndex(int idx)
    {
        this.first = idx;
    }


    /** {@inheritDoc} */
    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        this.second = idx;
    }


    /**
     * <p>getSecondQubitIndex.</p>
     *
     * @return a int
     */
    public int getSecondQubitIndex()
    {
        return this.second;
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return Arrays.asList(first, second);
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return highest;
    }


    /**
     * <p>setHighestAffectedQubitIndex.</p>
     *
     * @param v a int
     */
    public void setHighestAffectedQubitIndex(int v)
    {
        this.highest = v;
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
        return "TwoQubit";
    }


    /** {@inheritDoc} */
    @Override
    public int getSize()
    {
        return 2;
    }


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean v)
    {
        this.inverse = v;
    }


    /** {@inheritDoc} */
    @Override public String toString()
    {
        return "Gate acting on qubits " + first + " and " + second + " and caption " + getCaption();
    }
}
