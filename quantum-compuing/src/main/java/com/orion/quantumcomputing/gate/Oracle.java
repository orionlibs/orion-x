package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;
import com.orion.quantumcomputing.QuantumGate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Oracle implements QuantumGate
{
    private int mainQubit = 0;
    private List<Integer> affected = new LinkedList<>();
    private Complex[][] matrix;
    private String caption = "Oracle";
    private int span = 1;


    public Oracle(int i)
    {
        this.mainQubit = i;
    }


    public Oracle(Complex[][] matrix)
    {
        this.matrix = matrix;
        sanitizeMatrix();
        span = (int)(Math.log(matrix.length) / Math.log(2));
        for(int i = 0; i < span; i++)
        {
            setAdditionalQubit(i, i);
        }
    }


    @Override
    public int getSize()
    {
        return span;
    }


    @Override
    public int getMainQubitIndex()
    {
        return mainQubit;
    }


    @Override
    public void setMainQubitIndex(int idx)
    {
        this.mainQubit = 0;
    }


    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        this.affected.add(idx);
    }


    public int getQubits()
    {
        return span;
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return this.affected;
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(getAffectedQubitIndexes());
    }


    @Override
    public String getCaption()
    {
        return this.caption;
    }


    public void setCaption(String c)
    {
        this.caption = c;
    }


    @Override
    public String getName()
    {
        return "Oracle";
    }


    @Override
    public String getGroup()
    {
        return "Oracle";
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    private void sanitizeMatrix()
    {
        int rows = matrix.length;
        for(int i = 0; i < rows; i++)
        {
            Complex[] row = matrix[i];
            for(int j = 0; j < row.length; j++)
            {
                if(matrix[i][j] == null)
                {
                    matrix[i][j] = Complex.ZERO;
                }
            }
        }
    }


    @Override
    public void setInverse(boolean inv)
    {
        if(inv)
        {
            this.matrix = Complex.conjugateTranspose(matrix);
        }
    }
}
