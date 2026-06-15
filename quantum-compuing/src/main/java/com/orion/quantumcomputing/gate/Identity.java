package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;

public class Identity extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE}};


    public Identity()
    {
    }


    public Identity(int idx)
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
        return "I";
    }
}
