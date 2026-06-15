package com.orion.quantumcomputing.gate;

import Complex;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.redfx.strange.Gate;

/**
 * <p>Abstract InformalGate class.</p>
 *
 * @author alain
 * @version $Id: $Id
 */
public abstract class InformalGate implements Gate
{
    private List<Integer> affected = new LinkedList<>();
    private int mainIndex;


    /**
     * <p>Constructor for InformalGate.</p>
     */
    public InformalGate()
    {
        this(0);
    }


    /**
     * <p>Constructor for InformalGate.</p>
     *
     * @param idx a int
     */
    public InformalGate(int idx)
    {
        this.mainIndex = idx;
        affected.add(idx);
    }


    /** {@inheritDoc} */
    @Override
    public int getMainQubitIndex()
    {
        return 0;
    }


    /** {@inheritDoc} */
    @Override
    public void setMainQubitIndex(int idx)
    {
    }


    /** {@inheritDoc} */
    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(affected);
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return affected;
    }


    /** {@inheritDoc} */
    @Override
    public String getGroup()
    {
        return "informal";
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return new Complex[0][];
    }
}
