package com.orion.quantumcomputing.gate;

public class RotationY extends Rotation
{
    public RotationY(double theta, int idx)
    {
        super(theta, Axes.YAxis, idx);
    }


    @Override
    public String getCaption()
    {
        return "RotationY " + thetav;
    }
}
