package com.orion.quantumcomputing.gate;

import static Complex.ONE;
import static Complex.ZERO;

import Complex;
import org.redfx.strange.gate.ThreeQubitGate;

/**
 * <p>Toffoli class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Toffoli extends ThreeQubitGate
{
    Complex[][] matrix = new Complex[][] {
                    {ONE, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO},
                    {ZERO, ONE, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO},
                    {ZERO, ZERO, ONE, ZERO, ZERO, ZERO, ZERO, ZERO},
                    {ZERO, ZERO, ZERO, ONE, ZERO, ZERO, ZERO, ZERO},
                    {ZERO, ZERO, ZERO, ZERO, ONE, ZERO, ZERO, ZERO},
                    {ZERO, ZERO, ZERO, ZERO, ZERO, ONE, ZERO, ZERO},
                    {ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ONE},
                    {ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ONE, ZERO}
    };


    /**
     * <p>Constructor for Toffoli.</p>
     *
     * @param a a int
     * @param b a int
     * @param c a int
     */
    public Toffoli(int a, int b, int c)
    {
        super(a, b, c);
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    /** {@inheritDoc} */
    @Override
    public int getSize()
    {
        return 3;
    }


    /** {@inheritDoc} */
    @Override public void setInverse(boolean v)
    {
        // NOP
    }


    /** {@inheritDoc} */
    @Override public String getCaption()
    {
        return "CCnot";
    }
}
