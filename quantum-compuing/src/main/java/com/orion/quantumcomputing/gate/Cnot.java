package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.TwoQubitGate;

/**
 * <p>Cnot class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Cnot extends TwoQubitGate
{
    Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO}
    };


    /**
     * <p>Constructor for Cnot.</p>
     */
    public Cnot()
    {
    }


    /**
     * <p>Constructor for Cnot.</p>
     *
     * @param a a int
     * @param b a int
     */
    public Cnot(int a, int b)
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
        return "Cnot";
    }
}
