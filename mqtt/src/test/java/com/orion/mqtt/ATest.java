package com.orion.mqtt;

import java.io.IOException;
import java.io.InputStream;
import java.util.TimeZone;
import org.apache.commons.io.IOUtils;

public class ATest
{
    static
    {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    protected String loadResourceAsString(String fileLocation)
    {
        try
        {
            return IOUtils.toString(this.getClass().getResourceAsStream(fileLocation));
        }
        catch(IOException e)
        {
            return "";
        }
    }


    protected InputStream loadResourceAsStream(String fileLocation)
    {
        return this.getClass().getResourceAsStream(fileLocation);
    }


    protected void assertApproximate(double expected, double actual, double tolerance)
    {
        if(Math.abs(actual - expected) > tolerance)
        {
            new AssertionError("" + expected + "does not approximate " + actual + "given tolerance " + tolerance);
        }
    }
}
