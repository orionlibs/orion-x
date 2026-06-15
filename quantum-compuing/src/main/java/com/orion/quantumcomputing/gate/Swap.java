package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.TwoQubitGate;

/**
 * <p>Swap class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Swap extends TwoQubitGate
{
    Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE}
    };


    /**
     * <p>Constructor for Swap.</p>
     */
    public Swap()
    {
    }


    /**
     * <p>Constructor for Swap.</p>
     *
     * @param a a int
     * @param b a int
     */
    public Swap(int a, int b)
    {
        super(a, b);
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
        return "S";
    }
}
