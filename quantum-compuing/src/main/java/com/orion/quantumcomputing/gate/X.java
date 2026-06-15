package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;

public class X extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ZERO, Complex.ONE}, {Complex.ONE, Complex.ZERO}};


    public X(int idx)
    {
        super(idx);
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override
    public String getCaption()
    {
        return "X";
    }
}
