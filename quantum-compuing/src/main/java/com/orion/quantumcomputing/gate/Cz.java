package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.TwoQubitGate;

/**
 * <p>Cz class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Cz extends TwoQubitGate
{
    Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE.mul(-1)}
    };


    /**
     * <p>Constructor for Cz.</p>
     */
    public Cz()
    {
    }


    /**
     * <p>Constructor for Cz.</p>
     *
     * @param a a int
     * @param b a int
     */
    public Cz(int a, int b)
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
        return "Cz";
    }
}
