package com.orion.quantumcomputing;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BlockGate<T extends QuantumGate> implements QuantumGate
{
    protected Block block;
    protected int idx;
    protected boolean inverse = false;


    protected BlockGate()
    {
    }


    public BlockGate(Block block, int idx)
    {
        this.block = block;
        this.idx = idx;
    }


    protected final void setBlock(Block b)
    {
        this.block = b;
    }


    public final Block getBlock()
    {
        return this.block;
    }


    protected final void setIndex(int idx)
    {
        this.idx = idx;
    }


    @Override
    public void setMainQubitIndex(int idx)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public int getMainQubitIndex()
    {
        return idx;
    }


    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return IntStream.range(idx, idx + block.getNumberOfQubits()).boxed().collect(Collectors.toList());
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        return block.getNumberOfQubits() + idx - 1;
    }


    @Override
    public String getCaption()
    {
        return "B";
    }


    @Override
    public String getName()
    {
        return "BlockGate";
    }


    @Override
    public String getGroup()
    {
        return "BlockGroup";
    }


    @Override
    public Complex[][] getMatrix()
    {
        return getMatrix(null);
    }


    @Override
    public Complex[][] getMatrix(QuantumExecutor qee)
    {
        Complex[][] answer = block.getMatrix(qee);
        if(inverse)
        {
            answer = Complex.conjugateTranspose(answer);
        }
        return answer;
    }


    @Override
    public void setInverse(boolean inv)
    {
        this.inverse = inv;
    }


    public T inverse()
    {
        setInverse(!this.inverse);
        return (T)this;
    }


    @Override
    public int getSize()
    {
        return block.getNumberOfQubits();
    }


    @Override
    public boolean hasOptimization()
    {
        return true;
    }


    @Override
    public Complex[] applyOptimize(Complex[] v)
    {
        return block.applyOptimize(v, inverse);
    }


    @Override
    public String toString()
    {
        return "Gate for block " + block + ", size = " + getSize() + ", inv = " + inverse;
    }
}
