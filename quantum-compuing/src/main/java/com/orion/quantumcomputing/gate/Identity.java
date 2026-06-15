package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Identity class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Identity extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE}};


    /**
     * <p>Constructor for Identity.</p>
     */
    public Identity()
    {
    }


    /**
     * <p>Constructor for Identity.</p>
     *
     * @param idx a int
     */
    public Identity(int idx)
    {
        super(idx);
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    /** {@inheritDoc} */
    @Override public String getCaption()
    {
        return "I";
    }
}
