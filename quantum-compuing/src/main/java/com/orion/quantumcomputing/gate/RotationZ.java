package com.orion.quantumcomputing.gate;

public class RotationZ extends Rotation
{
    public RotationZ(double theta, int idx)
    {
        super(theta, Axes.ZAxis, idx);
    }


    @Override
    public String getCaption()
    {
        return "RotationZ " + this.thetav;
    }
}
