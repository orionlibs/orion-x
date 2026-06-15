package com.orion.quantumcomputing.gate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.redfx.strange.Gate;

/**
 *
 * This class describe a Gate that operates on three qubits. In a single
 * <code>Step</code>, there should not be two Gates that act on the same qubit.
 *
 * @author johan
 * @version $Id: $Id
 */
public abstract class ThreeQubitGate implements Gate
{
    private int first;
    private int second;
    private int third;


    /**
     * <p>Constructor for ThreeQubitGate.</p>
     */
    public ThreeQubitGate()
    {
    }


    /**
     * <p>Constructor for ThreeQubitGate.</p>
     *
     * @param first a int
     * @param second a int
     * @param third a int
     */
    public ThreeQubitGate(int first, int second, int third)
    {
        this.first = first;
        this.second = second;
        this.third = third;
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


    /**
     * <p>getMainQubit.</p>
     *
     * @return a int
     */
    public int getMainQubit()
    {
        return this.first;
    }


    /**
     * <p>getSecondQubit.</p>
     *
     * @return a int
     */
    public int getSecondQubit()
    {
        return this.second;
    }


    /**
     * <p>getThirdQubit.</p>
     *
     * @return a int
     */
    public int getThirdQubit()
    {
        return this.third;
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return Arrays.asList(first, second, third);
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(getAffectedQubitIndexes());
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
        return "ThreeQubit";
    }


    /** {@inheritDoc} */
    @Override public String toString()
    {
        return "Gate acting on qubits " + first + ", " + second + " and " + third + " and caption " + getCaption();
    }
}
