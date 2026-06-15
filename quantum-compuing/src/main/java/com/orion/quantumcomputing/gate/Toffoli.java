package com.orion.quantumcomputing.gate;

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


    public Toffoli(int a, int b, int c)
    {
        super(a, b, c);
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override
    public int getSize()
    {
        return 3;
    }


    @Override
    public void setInverse(boolean v)
    {
        // NOP
    }


    @Override
    public String getCaption()
    {
        return "CCnot";
    }
}
